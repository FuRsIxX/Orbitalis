#!/usr/bin/env python3
"""
Fabrique les sons d'Orbitalis et le fichier qui les déclare (sounds.json).

Les sons sont les fichiers rangés dans tools/sounds/orbitalis sound/, par dossier (pad, capsule, station) : chacun
est converti au format qu'attend le jeu (Ogg Vorbis, une seule voie), ramené à son niveau sonore (voir PAD_LEVELS et
DEDICATED) et rangé dans les ressources du mod. Un son du pas de tir sans fichier est synthétisé ici ; un son dédié
sans fichier reste muet.

Pour remplacer un son par le vôtre, voir tools/sounds/LISEZ-MOI.md.

Dépendance : ffmpeg (https://ffmpeg.org). Il est cherché dans le PATH, puis là où winget l'installe ; on peut aussi
donner son chemin dans la variable d'environnement FFMPEG.

Usage : python tools/generate_sounds.py
"""
import glob
import json
import math
import os
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import wave

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis")
SOUND_DIR = os.path.join(ASSETS, "sounds", "pad")
CUSTOM_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sounds")
# Les fichiers fournis, par dossier : pad (pas de tir et centre spatial), capsule (vol et cabine), station.
PROVIDED_DIR = os.path.join(CUSTOM_DIR, "orbitalis sound")
RATE = 44100


# ---------------------------------------------------------------------------
# Outils de synthèse : un son est une liste de nombres entre -1 et 1
# ---------------------------------------------------------------------------
def noise(seed, seconds):
    rnd = random.Random(seed)
    return [rnd.uniform(-1.0, 1.0) for _ in range(int(seconds * RATE))]


def silence(seconds):
    return [0.0] * int(seconds * RATE)


def lowpass(signal, cutoff):
    """Ne garde que les graves, sous la fréquence donnée (en Hz)."""
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    out, y = [], 0.0
    for x in signal:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(signal, cutoff):
    """Ne garde que les aigus, au-dessus de la fréquence donnée (en Hz)."""
    low = lowpass(signal, cutoff)
    return [x - l for x, l in zip(signal, low)]


def bandpass(signal, frequency, q):
    """Résonateur : ne laisse passer qu'une bande autour d'une fréquence, d'autant plus étroite que q est grand."""
    w = 2.0 * math.pi * frequency / RATE
    alpha = math.sin(w) / (2.0 * q)
    a0 = 1.0 + alpha
    b0, b2, a1, a2 = alpha / a0, -alpha / a0, -2.0 * math.cos(w) / a0, (1.0 - alpha) / a0
    out, x1, x2, y1, y2 = [], 0.0, 0.0, 0.0, 0.0
    for x in signal:
        y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x, y1, y
        out.append(y)
    return out


def brown(seed, seconds, leak=0.002):
    """Bruit brun : un grondement, dont l'énergie est toute dans les graves."""
    rnd = random.Random(seed)
    out, y = [], 0.0
    for _ in range(int(seconds * RATE)):
        y += rnd.uniform(-1.0, 1.0) * 0.06 - y * leak
        out.append(y)
    return out


def envelope(signal, attack, release, hold_until=None, curve=1.0):
    """Montée progressive au début, extinction progressive à la fin (durées en secondes)."""
    n = len(signal)
    end = n if hold_until is None else int(hold_until * RATE)
    out = []
    for i, x in enumerate(signal):
        t = i / RATE
        gain = min(1.0, t / attack) if attack > 0 else 1.0
        if i > end - int(release * RATE) and hold_until is None:
            gain *= max(0.0, (n - i) / (release * RATE)) ** curve
        elif hold_until is not None and i > end:
            gain *= max(0.0, 1.0 - (i - end) / max(1, n - end)) ** curve
        out.append(x * gain)
    return out


def shaped(signal, points):
    """Applique une enveloppe donnée par des points (temps en secondes, niveau), reliés par des droites."""
    out, k = [], 0
    for i, x in enumerate(signal):
        t = i / RATE
        while k + 1 < len(points) - 1 and t >= points[k + 1][0]:
            k += 1
        (t0, g0), (t1, g1) = points[k], points[k + 1]
        f = 0.0 if t1 <= t0 else max(0.0, min(1.0, (t - t0) / (t1 - t0)))
        out.append(x * (g0 + (g1 - g0) * f))
    return out


def mix(*signals):
    n = max(len(s) for s in signals)
    return [sum(s[i] for s in signals if i < len(s)) for i in range(n)]


def gain(signal, factor):
    return [x * factor for x in signal]


def place(signal, seconds):
    """Le même son, retardé."""
    return silence(seconds) + signal


def normalise(signal, peak=0.9):
    top = max(1e-9, max(abs(x) for x in signal))
    return [math.tanh(x / top * 1.4) / math.tanh(1.4) * peak for x in signal]


def ring(partials, seconds, decay):
    """
    Une pièce de métal qu'on frappe : ses modes de vibration, qui ne sont pas des multiples les uns des autres (c'est
    ce qui fait le timbre du métal), s'éteignent d'autant plus vite qu'ils sont aigus.
    """
    out = []
    for i in range(int(seconds * RATE)):
        t = i / RATE
        out.append(sum(level * math.sin(2 * math.pi * f * t + k) * math.exp(-t / (decay * (partials[0][0] / f) ** 0.6))
                       for k, (f, level) in enumerate(partials)))
    return out


def clunk(seed, weight=1.0, bright=1.0):
    """
    Un choc de métal lourd : le coup sourd de la masse qui arrive en butée, l'éclat bref du contact, puis la structure
    qui sonne. {@code weight} l'alourdit, {@code bright} lui donne plus d'éclat.
    """
    seconds = 0.9
    thud = [math.sin(2 * math.pi * (58 / weight) * (i / RATE) * (1.0 + 0.6 * math.exp(-i / RATE / 0.02))) * math.exp(-i / RATE / (0.09 * weight))
            for i in range(int(seconds * RATE))]
    click = [x * math.exp(-i / RATE / 0.006) for i, x in enumerate(bandpass(noise(seed, 0.08), 2600, 1.2))]
    body = [x * math.exp(-i / RATE / 0.05) for i, x in enumerate(lowpass(noise(seed + 1, 0.4), 900))]
    steel = ring([(212 / weight, 1.0), (547 / weight, 0.7), (911 / weight, 0.5), (1408, 0.35 * bright), (2233, 0.22 * bright),
                  (3170, 0.12 * bright)], seconds, 0.22 * weight)
    return mix(gain(thud, 1.5 * weight), gain(click, 1.6 * bright), gain(body, 1.2), gain(steel, 0.32))


def motor(seconds, speed, seed, teeth=11):
    """
    Un groupe hydraulique : le moteur électrique et ses harmoniques, le chant de la pompe (ses pistons, à plusieurs
    fois le régime), et le souffle de l'huile dans les flexibles, que la pompe fait battre. {@code speed} donne le
    régime, en tours par seconde, en fonction du temps.
    """
    hiss = bandpass(noise(seed, seconds), 1900, 0.9)
    out, phase = [], 0.0
    for i in range(int(seconds * RATE)):
        t = i / RATE
        f = speed(t)
        phase += 2 * math.pi * f / RATE
        load = f / 60.0
        hum = math.sin(phase) + 0.55 * math.sin(2 * phase + 0.4) + 0.3 * math.sin(3 * phase) + 0.18 * math.sin(5 * phase + 1.1)
        whine = 0.32 * math.sin(teeth * phase) + 0.14 * math.sin(2 * teeth * phase + 0.7) + 0.2 * math.sin((teeth - 0.5) * phase)
        ripple = 0.6 + 0.4 * math.sin(teeth * phase)
        out.append((hum * 0.5 + whine * load * 0.7 + hiss[i] * 0.5 * ripple * load) * min(1.0, load * 1.2))
    return out


# ---------------------------------------------------------------------------
# Les sons
# ---------------------------------------------------------------------------
def purge():
    """
    Purge : la soupape s'ouvre d'un coup, le gaz sous pression s'échappe en hurlant dans la conduite, puis le jet
    faiblit et descend dans les graves à mesure que la pression tombe.
    """
    seconds = 3.4
    raw = noise(11, seconds)
    jet_high = highpass(raw, 2600)
    jet_mid = bandpass(raw, 1400, 0.8)
    pipe = bandpass(raw, 420, 3.0)
    turbulence = lowpass(noise(12, seconds), 14)
    out = []
    for i in range(int(seconds * RATE)):
        t = i / RATE
        pressure = math.exp(-t / 1.05)
        flutter = 1.0 + 2.5 * turbulence[i]
        out.append((jet_high[i] * pressure ** 1.6 + jet_mid[i] * 0.9 * pressure + pipe[i] * 1.4 * pressure ** 0.6) * flutter)
    pop = gain(clunk(13, weight=0.6, bright=1.4), 0.5)
    return envelope(mix(out, pop), 0.004, 0.5)


def valve():
    """Vanne : le claquement sec de l'électrovanne, sa résonance dans la tuyauterie, puis un bref souffle de gaz."""
    seconds = 1.1
    raw = noise(21, seconds)
    breath = bandpass(raw, 2100, 0.7)
    out = [breath[i] * 0.8 * math.exp(-max(0.0, i / RATE - 0.04) / 0.2) * (1.0 if i / RATE > 0.03 else 0.0) for i in range(len(raw))]
    pipe = ring([(318, 1.0), (764, 0.6), (1290, 0.4), (2105, 0.2)], seconds, 0.12)
    return envelope(mix(gain(clunk(22, weight=0.55, bright=1.6), 1.0), out, gain(pipe, 0.25)), 0.001, 0.15)


def hydraulic():
    """
    Retrait d'un bras ombilical, du début à la fin :
    les verrous lâchent (deux claquements de métal), le groupe hydraulique monte en régime, le bras se met en marche
    dans un grondement sourd pendant que ses galets passent les joints du rail, puis il arrive en butée (un choc
    lourd, qui fait sonner la tour), le moteur retombe et le circuit se décharge dans un soupir.
    """
    seconds = 5.2

    def speed(t):
        if t < 0.35:
            return 0.0
        if t < 1.1:
            return 96.0 * ((t - 0.35) / 0.75) ** 0.6
        if t < 3.75:
            # En charge, le régime fléchit un peu et bat lentement.
            return 96.0 - 7.0 * math.sin((t - 1.1) * 1.9) ** 2 + 1.5 * math.sin(t * 23.0)
        return max(0.0, 96.0 * (1.0 - (t - 3.75) / 0.7) ** 1.5) if t < 4.45 else 0.0

    pump = lowpass(motor(seconds, speed, 31), 5200)
    # Le bras qui roule : un grondement, plus fort au milieu de la course.
    travel = shaped(bandpass(brown(32, seconds), 95, 0.7), [(0.0, 0.0), (0.9, 0.0), (1.5, 1.0), (3.5, 1.0), (3.8, 0.0), (seconds, 0.0)])
    # Les galets sur les joints du rail : des chocs espacés, qui se resserrent puis s'espacent avec la vitesse du bras.
    ticks, t = [], 1.0
    rnd = random.Random(33)
    while t < 3.7:
        pace = 0.5 + 0.5 * math.sin(math.pi * (t - 1.0) / 2.7)
        ticks.append(place(gain(clunk(34 + len(ticks), weight=0.5, bright=0.7), 0.16 + 0.1 * rnd.random()), t))
        t += 0.34 - 0.2 * pace + 0.03 * rnd.random()
    # La structure qui se plaint sous l'effort : une note de métal qui glisse.
    groan, phase = [], 0.0
    grit = lowpass([abs(x) for x in noise(35, seconds)], 22)
    for i in range(int(seconds * RATE)):
        tt = i / RATE
        phase += 2 * math.pi * (128.0 + 30.0 * math.sin(tt * 2.3) - 9.0 * tt) / RATE
        level = max(0.0, math.sin(math.pi * (tt - 1.2) / 2.5)) if 1.2 < tt < 3.7 else 0.0
        groan.append((math.sin(phase) + 0.5 * math.sin(2.76 * phase) + 0.3 * math.sin(5.4 * phase)) * grit[i] * 5.0 * level)
    # Le circuit qui se décharge, une fois le bras rangé.
    sigh = place(shaped(highpass(noise(36, 0.9), 1700), [(0.0, 0.0), (0.03, 1.0), (0.9, 0.0)]), 4.05)
    return envelope(mix(
        gain(clunk(37, weight=0.7, bright=1.5), 0.45), place(gain(clunk(38, weight=0.8, bright=1.3), 0.35), 0.17),
        gain(pump, 0.42), gain(travel, 5.5), gain(groan, 0.16), gain(sigh, 0.22),
        place(gain(clunk(39, weight=1.5, bright=0.9), 0.8), 3.75), *ticks), 0.002, 0.3)


def deluge():
    """
    Déluge : des tonnes d'eau qui s'abattent sur le pas de tir. Trois couches : le grondement de la masse d'eau, le
    fracas de ce qui frappe l'acier, et le sifflement des buses ; par-dessus, des paquets d'eau qui claquent.
    """
    seconds = 8.0
    raw = noise(41, seconds)
    roar = bandpass(brown(42, seconds), 140, 0.6)
    splash = bandpass(raw, 900, 0.5)
    spray = highpass(raw, 3800)
    swell = lowpass(noise(43, seconds), 3)
    gusts = lowpass([abs(x) ** 6 for x in noise(44, seconds)], 30)
    out = [(roar[i] * 9.0 + splash[i] * (1.0 + 14.0 * gusts[i]) + spray[i] * 0.45) * (1.0 + 2.0 * swell[i]) for i in range(len(raw))]
    return envelope(out, 1.2, 2.4)


def ignition():
    """
    Allumage : le coup sourd des moteurs qui prennent, puis le grondement qui enfle, tient, et s'éloigne avec la
    fusée. Ce qui fait le son d'une fusée, c'est son crépitement : des ondes de choc en rafales, dont le front monte
    d'un coup. On les fabrique en redressant un bruit grave, ce qui lui donne ces fronts raides.
    """
    seconds = 12.0
    n = int(seconds * RATE)
    deep = lowpass(brown(51, seconds), 70)
    body = bandpass(noise(52, seconds), 260, 0.5)
    tearing = lowpass(noise(53, seconds), 1500)
    bursts = lowpass([abs(x) ** 3 for x in noise(54, seconds)], 18)
    crackle = highpass([math.copysign(abs(x) ** 0.4, x) * (0.2 + 9.0 * bursts[i]) for i, x in enumerate(tearing)], 300)
    out = [deep[i] * 22.0 + body[i] * 2.2 + crackle[i] * 0.9 for i in range(n)]
    # La combustion bat lentement, comme si elle cherchait son régime.
    out = [x * (1.0 + 0.2 * math.sin(2 * math.pi * 9 * i / RATE) * math.sin(2 * math.pi * 0.7 * i / RATE)) for i, x in enumerate(out)]
    whump = [math.sin(2 * math.pi * 38 * (i / RATE)) * math.exp(-i / RATE / 0.35) * 9.0 for i in range(int(1.5 * RATE))]
    out = mix(envelope(out, 1.1, 0.0, hold_until=5.0, curve=1.7), whump)
    # En s'éloignant, la fusée perd ses aigus : l'air les mange.
    far = lowpass(out, 600)
    return [x + (f - x) * max(0.0, min(1.0, (i / RATE - 5.0) / 5.0)) for i, (x, f) in enumerate(zip(out, far))]


def creak():
    """
    Grincement : l'acier d'une passerelle qui travaille. C'est un frottement saccadé : la pièce colle, cède, recolle,
    et chaque à-coup fait sonner la structure. Les à-coups s'espacent à mesure que le mouvement ralentit.
    """
    seconds = 2.4
    n = int(seconds * RATE)
    rnd = random.Random(61)
    excitation = [0.0] * n
    t = 0.03
    while t < seconds - 0.4:
        progress = t / seconds
        excitation[int(t * RATE)] = (0.5 + rnd.random()) * (1.0 - 0.6 * progress)
        # Vite au début (un cri), de plus en plus lent ensuite (des craquements).
        t += (0.004 + 0.05 * progress ** 2) * (0.7 + 0.6 * rnd.random())
    modes = [(190, 14), (447, 22), (731, 28), (1190, 30), (1984, 26), (2890, 20)]
    out = mix(*[gain(bandpass(excitation, f, q), 1.0 / (1 + k * 0.5)) for k, (f, q) in enumerate(modes)])
    rub = shaped(bandpass(noise(62, seconds), 1500, 0.8), [(0.0, 0.0), (0.1, 0.05), (1.0, 0.03), (seconds, 0.0)])
    return envelope(mix(out, rub), 0.01, 0.5)


# Nom de l'événement sonore dans le jeu, la fonction qui le synthétise, et le lieu où on l'entend : c'est ffmpeg
# qui l'y place, après coup, par des échos (la tour et le tablier renvoient le son ; le carneau le fait gronder).
OUTDOORS = "aecho=0.9:0.55:38|92|171:0.30|0.18|0.10"
STEELWORK = "aecho=0.9:0.6:23|61|127:0.34|0.22|0.12"
TRENCH = "aecho=0.9:0.7:70|160|310:0.35|0.25|0.15"
SOUNDS = {
    "pad_purge": (purge, OUTDOORS),
    "pad_valve": (valve, STEELWORK),
    "pad_hydraulic": (hydraulic, STEELWORK),
    "pad_deluge": (deluge, OUTDOORS),
    "pad_ignition": (ignition, TRENCH),
    "pad_creak": (creak, STEELWORK + ",aecho=0.9:0.5:210:0.2"),
}


# ---------------------------------------------------------------------------
# Écriture
# ---------------------------------------------------------------------------
def find_ffmpeg():
    candidates = [os.environ.get("FFMPEG"), shutil.which("ffmpeg")]
    local = os.environ.get("LOCALAPPDATA")
    if local:
        candidates += glob.glob(os.path.join(local, "Microsoft", "WinGet", "Packages", "Gyan.FFmpeg*", "*", "bin", "ffmpeg.exe"))
        candidates.append(os.path.join(local, "Microsoft", "WinGet", "Links", "ffmpeg.exe"))
    for candidate in candidates:
        if candidate and os.path.exists(candidate):
            return candidate
    sys.exit("ffmpeg est introuvable : installez-le (winget install Gyan.FFmpeg) ou donnez son chemin dans FFMPEG.")


def write_wav(path, signal):
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, x)) * 32767)) for x in signal))


def make_loop(ffmpeg, source, target, scratch):
    """
    Fabrique une boucle sans couture à partir d'un son.
    <ol>
    1. On en prend la partie stable, la plus longue possible : après la montée, avant l'extinction. Plus la boucle
       est longue, moins on l'entend revenir.
    2. On en égalise le niveau : un son qui faiblit lentement ferait un à-coup à chaque retour. Le niveau est mesuré
       seconde par seconde, et ramené partout à la même valeur, en douceur.
    3. On fond sa fin dans son début, sur trois secondes, à puissance constante : ni clic ni creux à la couture.
    </ol>
    Une seule voie, et un fichier chargé en mémoire : le jeu le répète alors lui-même, sans la moindre coupure.
    """
    raw = os.path.join(scratch, "loop_source.wav")
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-ac", "1", "-ar", str(RATE), "-c:a", "pcm_s16le", raw], check=True)
    with wave.open(raw, "rb") as f:
        frames = struct.unpack("<%dh" % f.getnframes(), f.readframes(f.getnframes()))
    seconds = len(frames) / RATE
    start = min(5.0, seconds * 0.25)
    end = max(start + 2.0, min(start + 24.0, seconds - min(4.0, seconds * 0.15)))
    part = [x / 32768.0 for x in frames[int(start * RATE):int(end * RATE)]]
    # Niveau, par tranches d'une demi-seconde, puis gain qui le ramène à la moyenne (borné, et lissé d'une tranche à l'autre).
    step = RATE // 2
    levels = []
    for k in range(0, len(part), step):
        chunk = part[k:k + step]
        levels.append(math.sqrt(sum(x * x for x in chunk) / max(1, len(chunk))))
    mean = sum(levels) / len(levels)
    gains = [max(0.5, min(2.5, mean / max(1e-6, level))) for level in levels]
    gains = [(gains[max(0, k - 1)] + gains[k] + gains[min(len(gains) - 1, k + 1)]) / 3.0 for k in range(len(gains))]
    flat = []
    for i, x in enumerate(part):
        k = i / step - 0.5
        a = max(0, min(len(gains) - 1, int(math.floor(k))))
        b = min(len(gains) - 1, a + 1)
        t = max(0.0, min(1.0, k - a))
        flat.append(x * (gains[a] * (1.0 - t) + gains[b] * t))
    cross = int(min(3.0, (end - start) / 4.0) * RATE)
    body = len(flat) - cross
    loop = flat[:body]
    for i in range(cross):
        t = i / cross
        loop[i] = flat[i] * math.sqrt(t) + flat[body + i] * math.sqrt(1.0 - t)
    top = max(1e-9, max(abs(x) for x in loop))
    shaped = os.path.join(scratch, "loop.wav")
    write_wav(shaped, [x / top * 0.93 for x in loop])
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", shaped, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", target], check=True)


# Niveau perçu visé pour un son du pas de tir fourni en .wav, .mp3 ou .flac, en LUFS (plus près de zéro : plus fort).
# Un .ogg à une voie est repris tel quel. Repères : les moteurs sont vers -13, l'explosion proche à -11.
PAD_LEVELS = {"pad_hydraulic": -14.0, "pad_purge": -12.0, "pad_valve": -16.0}
# Plafond des crêtes d'un son ramené à son niveau (0,85 : environ -1,4 dB) : la marge absorbe ce qu'ajoute l'encodage.
CEILING = 0.85

# Volume des sons trop présents (1 : tel que le fichier).
# La vanne n'y est plus : ramenée à son niveau, elle doit s'entendre de la console (un volume réduit raccourcit aussi la portée).
VOLUMES = {"pad_deluge": 0.5, "pad_deluge_loop": 0.9}


def channels(ffmpeg, path):
    """Nombre de voies d'un fichier son, lu dans le rapport de ffmpeg (stéréo : 2)."""
    report = subprocess.run([ffmpeg, "-hide_banner", "-i", path], capture_output=True, text=True).stderr
    return 2 if "stereo" in report or "5.1" in report else 1


def custom_file(name):
    """Fichier fourni pour ce son du pas de tir (pad_purge : orbitalis sound/pad/purge.wav), s'il y en a un."""
    for extension in ("wav", "ogg", "mp3", "flac"):
        for path in (os.path.join(PROVIDED_DIR, "pad", name[len("pad_"):] + "." + extension), os.path.join(CUSTOM_DIR, name + "." + extension)):
            if os.path.exists(path):
                return path
    return None


def provided(folder, name):
    """Fichier fourni sous ce nom dans ce dossier d'orbitalis sound, ou None."""
    for extension in ("wav", "ogg", "mp3", "flac"):
        path = os.path.join(PROVIDED_DIR, folder, name + "." + extension)
        if os.path.exists(path):
            return path
    return None


def level_to(ffmpeg, source, target, wanted):
    """
    Convertit un fichier pour le jeu (Ogg Vorbis, une seule voie, 44,1 kHz) en le ramenant au niveau perçu voulu, en
    LUFS. Le gain se cherche en trois passes ; un limiteur garde les crêtes sous CEILING.

    @return le niveau et la crête obtenus, et le gain appliqué
    """
    # Le limiteur rattrape son retard (latency) : sans cela, il décale le son de quelques millisecondes, et une boucle
    # qui se raccordait parfaitement claque à chaque tour.
    shape = "aformat=channel_layouts=mono"
    gain = 0.0
    for _ in range(3):
        chain = "%s,volume=%.2fdB,alimiter=limit=%.2f:attack=3:release=80:level=disabled:latency=1" % (shape, gain, CEILING)
        measured, _ = loudness(ffmpeg, source, chain)
        gain += wanted - measured
    chain = "%s,volume=%.2fdB,alimiter=limit=%.2f:attack=3:release=80:level=disabled:latency=1" % (shape, gain, CEILING)
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-af", chain, "-ar", "44100", "-c:a", "libvorbis", "-q:a", "6", target],
                   check=True)
    level, peak = loudness(ffmpeg, target)
    return level, peak, gain


def duration(ffmpeg, path):
    """Durée d'un fichier son, en secondes."""
    report = subprocess.run([ffmpeg, "-hide_banner", "-i", path], capture_output=True, text=True, errors="replace").stderr
    clock = report.split("Duration:")[1].split(",")[0].strip().split(":")
    return int(clock[0]) * 3600 + int(clock[1]) * 60 + float(clock[2])


# Les sons dédiés : nom de l'événement dans le jeu, dossier (celui d'orbitalis sound, et celui des ressources), fichiers
# fournis (plusieurs : des variantes, que le jeu tire au hasard), niveau perçu visé en LUFS. Les niveaux s'étagent : les
# chocs du vol (séparation, atterrissage) et l'alarme du pas de tir en haut, puis les moteurs et les bruits de la
# cabine, les alertes de bord (franches sans être criardes : elles peuvent sonner ensemble), et tout en bas les
# ambiances de la station, qui doivent se faire oublier.
DEDICATED = [
    ("part_placed", "pad", ["part_placed_1", "part_placed_2", "part_placed_3", "part_placed_4", "part_placed_5"], -16.0),
    ("warning_overpressure", "pad", ["warning_overpressure"], -12.0),
    ("stage_separation", "capsule", ["stage_separation"], -12.0),
    ("fairing_jettison", "capsule", ["fairing_jettison"], -13.0),
    ("reentry_loop", "capsule", ["reentry_loop"], -13.0),
    ("parachute_deploy", "capsule", ["parachute_deploy"], -16.0),
    ("parachute_deploy_loop", "capsule", ["parachute_deploy_loop"], -21.0),
    ("capsule_landing", "capsule", ["capsule_landing"], -12.0),
    ("lander_engine", "capsule", ["lander_engine"], -13.0),
    ("capsule_alert_maneuver", "capsule", ["capsule_alert_maneuver"], -19.0),
    ("capsule_alert_imminent_crash", "capsule", ["capsule_alert_imminent_crash"], -17.0),
    ("capsule_alert_stall", "capsule", ["capsule_alert_stall"], -18.0),
    ("capsule_alert_deploy", "capsule", ["capsule_alert_deploy"], -18.0),
    ("airlock_cycle", "station", ["airlock_cycle"], -15.0),
    ("airlock_hatch_open", "station", ["airlock_hatch open"], -16.0),
    ("airlock_hatch_close", "station", ["airlock_hatch close"], -16.0),
    ("docking", "station", ["docking"], -13.0),
    ("undocking", "station", ["undocking"], -14.0),
    ("life_support_hum", "station", ["life_support_hum"], -19.0),
    ("station_ambient", "station", ["station_ambient_noise"], -21.0),
]


# Boucles dont le fichier ne se raccorde pas tout seul (un saut au retour, qui claque à chaque tour) : leur fin est fondue
# dans leur début (voir seamless). Le moteur de descente part du silence : sans cela, il s'éteindrait à chaque tour ; les
# ambiances de la station sont si calmes qu'un léger saut y ferait un tic.
SEAMLESS = {"lander_engine", "life_support_hum", "station_ambient"}


def seamless(ffmpeg, source, scratch):
    """
    Raccorde une boucle courte sans la raccourcir beaucoup : ses dernières secondes sont fondues dans ses premières, à
    puissance constante, sur un quart de sa durée au plus (une demi-seconde). Ni clic ni creux au retour.

    @return le fichier raccordé (une voie, en .wav)
    """
    raw = os.path.join(scratch, "seamless_source.wav")
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-ac", "1", "-ar", str(RATE), "-c:a", "pcm_s16le", raw], check=True)
    with wave.open(raw, "rb") as f:
        frames = [x / 32768.0 for x in struct.unpack("<%dh" % f.getnframes(), f.readframes(f.getnframes()))]
    cross = int(min(0.5, len(frames) / RATE / 4.0) * RATE)
    body = len(frames) - cross
    loop = frames[:body]
    for i in range(cross):
        t = i / cross
        loop[i] = frames[i] * math.sqrt(t) + frames[body + i] * math.sqrt(1.0 - t)
    top = max(1e-9, max(abs(x) for x in loop))
    joined = os.path.join(scratch, "seamless.wav")
    write_wav(joined, [x / max(1.0, top) for x in loop])
    return joined


# Les sons qui jouent en boucle : ils sont ramenés à leur niveau sans perdre leur raccord (voir level_loop).
LOOPS = {"reentry_loop", "parachute_deploy_loop", "lander_engine", "capsule_alert_imminent_crash", "capsule_alert_stall",
         "capsule_alert_deploy", "life_support_hum", "station_ambient"}


def read_mono(ffmpeg, source, scratch):
    """Les échantillons d'un fichier, sur une voie, entre -1 et 1."""
    raw = os.path.join(scratch, "mono.wav")
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-ac", "1", "-ar", str(RATE), "-c:a", "pcm_s16le", raw], check=True)
    with wave.open(raw, "rb") as f:
        return [x / 32768.0 for x in struct.unpack("<%dh" % f.getnframes(), f.readframes(f.getnframes()))]


def level_loop(ffmpeg, source, target, wanted, scratch):
    """
    Comme level_to, pour une boucle. Le limiteur ne part pas du même état au début du fichier qu'à sa fin : appliqué
    tel quel, il casse le raccord. On le fait donc travailler sur trois tours de la boucle mis bout à bout, et l'on
    garde celui du milieu, où il a le même état à ses deux bords.
    """
    frames = read_mono(ffmpeg, source, scratch)
    n = len(frames)
    triple = os.path.join(scratch, "triple.wav")
    write_wav(triple, frames * 3)
    middle = os.path.join(scratch, "middle.wav")
    limited = os.path.join(scratch, "limited.wav")
    gain = 0.0
    for _ in range(4):
        chain = "volume=%.2fdB,alimiter=limit=%.2f:attack=3:release=80:level=disabled:latency=1" % (gain, CEILING)
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", triple, "-af", chain, "-c:a", "pcm_s16le", limited], check=True)
        with wave.open(limited, "rb") as f:
            out = [x / 32768.0 for x in struct.unpack("<%dh" % f.getnframes(), f.readframes(f.getnframes()))]
        write_wav(middle, out[n:2 * n])
        measured, _ = loudness(ffmpeg, middle)
        gain += wanted - measured
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", middle, "-c:a", "libvorbis", "-q:a", "6", target], check=True)
    level, peak = loudness(ffmpeg, target)
    return level, peak, gain - (wanted - measured)


# Sons coupés avant leur fin, en secondes (avec un fondu de 50 ms) : la fin du cycle du sas est un second bruit
# d'ouverture, qui doublait celui de l'écoutille, joué juste après.
TRIM = {"airlock_cycle": 1.15}


def dedicated(ffmpeg, declared, only=None):
    """Convertit les sons dédiés fournis et les déclare ; un son sans fichier est déclaré muet."""
    for event, folder, files, wanted in DEDICATED:
        if only and event not in only:
            continue
        out_dir = os.path.join(ASSETS, "sounds", folder)
        os.makedirs(out_dir, exist_ok=True)
        names = []
        for number, file in enumerate(files, 1):
            short = event if len(files) == 1 else "%s_%d" % (event, number)
            target = os.path.join(out_dir, short + ".ogg")
            source = provided(folder, file)
            if source is None:
                if os.path.exists(target):
                    os.remove(target)
                print("%-30s aucun fichier « %s/%s » : muet" % (event, folder, file))
                continue
            if event in LOOPS:
                with tempfile.TemporaryDirectory() as scratch:
                    looped = seamless(ffmpeg, source, scratch) if event in SEAMLESS else source
                    level, peak, gain = level_loop(ffmpeg, looped, target, wanted, scratch)
            elif event in TRIM:
                with tempfile.TemporaryDirectory() as scratch:
                    cut = os.path.join(scratch, "cut.wav")
                    end = TRIM[event]
                    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-af",
                                    "atrim=0:%.3f,afade=t=out:st=%.3f:d=0.05" % (end, end - 0.05), cut], check=True)
                    level, peak, gain = level_to(ffmpeg, cut, target, wanted)
            else:
                level, peak, gain = level_to(ffmpeg, source, target, wanted)
            names.append("orbitalis:%s/%s" % (folder, short))
            print("%-30s %-24s %5.2f s  %5.1f ko  %6.1f LUFS  crête %5.1f dBFS  (gain %+.1f dB)"
                  % (event, file, duration(ffmpeg, target), os.path.getsize(target) / 1024.0, level, peak, gain))
        declared[event] = {"subtitle": "subtitles.orbitalis." + event, "sounds": names}


# Sons d'explosion : ils ne sont pas synthétisés, ce sont les fichiers déposés dans tools/sounds/explosion/. Chaque
# début de nom désigne un son du jeu ; tous les fichiers qui commencent ainsi en sont des variantes, et le jeu en
# tire une au hasard à chaque fois.
EXPLOSION_SOURCES = os.path.join(CUSTOM_DIR, "explosion")
EXPLOSION_DIR = os.path.join(ASSETS, "sounds", "explosion")
EXPLOSIONS = {
    "explosion proche": ("explosion_near", "near"),
    "explosion moyenne distance": ("explosion_mid", "mid"),
    "explosion longue distance": ("explosion_far", "far"),
    "chute debris": ("debris_fall", "debris"),
    # La montée en pression des réservoirs, vannes oubliées ouvertes : le jeu le lance pour qu'il finisse à l'instant
    # où ils cèdent. Sa durée compte donc : voir OVERPRESSURE_SOUND_TICKS dans LaunchTableBlockEntity.
    "overfiled pressure": ("pad_overpressure", "pressure"),
}
# Niveau perçu visé pour chaque son, en LUFS (plus près de zéro : plus fort). L'explosion proche est le son le plus
# fort du mod, un peu au-dessus des moteurs ; les autres s'étagent en dessous. Les variantes d'un même son sortent
# ainsi au même niveau, quel que soit celui du fichier fourni.
EXPLOSION_LEVELS = {"explosion_near": -11.0, "explosion_mid": -13.0, "explosion_far": -16.0, "debris_fall": -15.0, "pad_overpressure": -14.0}
# Plafond des crêtes (0,8 : environ -2 dB). La marge absorbe ce que l'encodage Vorbis rajoute : le son ne sature
# jamais, c'est ce qui le garde fort sans le rendre agressif.
EXPLOSION_CEILING = 0.8
# Ce que le limiteur a le droit de raboter sur les crêtes, en dB, pour gagner en niveau.
EXPLOSION_SQUASH = {"explosion_near": 9.0, "explosion_mid": 7.0, "explosion_far": 6.0, "debris_fall": 6.0, "pad_overpressure": 4.0}


def loudness(ffmpeg, path, filters=None):
    """Niveau perçu (LUFS) et crête vraie (dBFS) d'un fichier, après ces filtres s'il y en a."""
    chain = (filters + "," if filters else "") + "ebur128=peak=true"
    report = subprocess.run([ffmpeg, "-hide_banner", "-nostats", "-i", path, "-af", chain, "-f", "null", "-"],
                            capture_output=True, text=True, errors="replace").stderr
    summary = report[report.rfind("Summary:"):]
    level = float(summary.split("I:")[1].split("LUFS")[0])
    peak = float(summary.split("Peak:")[1].split("dBFS")[0])
    return level, peak


def explosions(ffmpeg, declared):
    """Convertit les sons d'explosion fournis (Ogg Vorbis, une seule voie) et les déclare, variantes comprises."""
    if not os.path.isdir(EXPLOSION_SOURCES):
        return
    os.makedirs(EXPLOSION_DIR, exist_ok=True)
    for old in glob.glob(os.path.join(EXPLOSION_DIR, "*.ogg")):
        os.remove(old)
    for prefix, (event, short) in EXPLOSIONS.items():
        files = sorted(f for f in os.listdir(EXPLOSION_SOURCES)
                       if (f.lower().startswith(prefix + " ") or f.lower().rsplit(".", 1)[0] == prefix)
                       and f.lower().rsplit(".", 1)[-1] in ("wav", "ogg", "mp3", "flac"))
        names = []
        for number, file in enumerate(files, 1):
            target = os.path.join(EXPLOSION_DIR, "%s_%d.ogg" % (short, number))
            source = os.path.join(EXPLOSION_SOURCES, file)
            # Une seule voie : le jeu ne situe dans l'espace que les sons mono. Le son est monté vers son niveau visé,
            # mais jamais au point d'écraser ses crêtes de plus que ne le permet EXPLOSION_SQUASH : une explosion est un coup
            # bref suivi d'une longue traîne, et c'est ce contraste qui la rend puissante.
            shape = "aformat=channel_layouts=mono"
            _, source_peak = loudness(ffmpeg, source, shape)
            most = 20.0 * math.log10(EXPLOSION_CEILING) - source_peak + EXPLOSION_SQUASH[event]
            gain = 0.0
            for _ in range(3):
                chain = "%s,volume=%.2fdB,alimiter=limit=%.2f:attack=3:release=120:level=disabled" % (shape, gain, EXPLOSION_CEILING)
                measured, _ = loudness(ffmpeg, source, chain)
                gain = min(most, gain + EXPLOSION_LEVELS[event] - measured)
            chain = "%s,volume=%.2fdB,alimiter=limit=%.2f:attack=3:release=120:level=disabled" % (shape, gain, EXPLOSION_CEILING)
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-af", chain, "-ar", "44100",
                            "-c:a", "libvorbis", "-q:a", "6", target], check=True)
            level, peak = loudness(ffmpeg, target)
            names.append("orbitalis:explosion/%s_%d" % (short, number))
            print("%-14s %-34s %5.1f ko  %6.1f LUFS  crête %5.1f dBFS  (gain %+.1f dB)" % (event, file, os.path.getsize(target) / 1024.0, level, peak, gain))
            if event == "pad_overpressure":
                report = subprocess.run([ffmpeg, "-hide_banner", "-i", target], capture_output=True, text=True, errors="replace").stderr
                clock = report.split("Duration:")[1].split(",")[0].strip().split(":")
                seconds = int(clock[0]) * 3600 + int(clock[1]) * 60 + float(clock[2])
                print("%-14s dure %.2f s : OVERPRESSURE_SOUND_TICKS doit valoir %d" % (event, seconds, round(seconds * 20)))
        if names:
            declared[event] = {"subtitle": "subtitles.orbitalis." + event, "sounds": names}
        else:
            print("%-14s aucun fichier « %s N » dans tools/sounds/explosion : ce son sera muet" % (event, prefix))


if __name__ == "__main__":
    ffmpeg = find_ffmpeg()
    if sys.argv[1:] == ["dedies"]:
        # Les sons dédiés seuls : ceux du pas de tir et les explosions ne sont pas refaits.
        with open(os.path.join(ASSETS, "sounds.json")) as f:
            declared = json.load(f)
        dedicated(ffmpeg, declared)
        with open(os.path.join(ASSETS, "sounds.json"), "w", newline="\n") as f:
            json.dump(declared, f, indent=2)
        sys.exit(0)
    if sys.argv[1:] == ["explosions"]:
        # Les sons d'explosion seuls : ceux du pas de tir ne sont pas refaits, leur déclaration est gardée telle quelle.
        with open(os.path.join(ASSETS, "sounds.json")) as f:
            declared = json.load(f)
        for event, _ in EXPLOSIONS.values():
            declared.pop(event, None)
        explosions(ffmpeg, declared)
        with open(os.path.join(ASSETS, "sounds.json"), "w", newline="\n") as f:
            json.dump(declared, f, indent=2)
        sys.exit(0)
    os.makedirs(SOUND_DIR, exist_ok=True)
    declared = {}
    # Des noms de sons en arguments (« pad_purge », « docking ») : eux seuls sont refaits, les autres ne sont pas touchés.
    only = sys.argv[1:]
    if only:
        known = list(SOUNDS) + [entry[0] for entry in DEDICATED]
        unknown = [name for name in only if name not in known]
        if unknown:
            sys.exit("Son inconnu : %s. Sons connus : %s, ou « explosions », ou « dedies »." % (", ".join(unknown), ", ".join(known)))
        with open(os.path.join(ASSETS, "sounds.json")) as f:
            declared = json.load(f)
    with tempfile.TemporaryDirectory() as scratch:
        for name, (synthesise, place_filter) in SOUNDS.items():
            if only and name not in only:
                continue
            source = custom_file(name)
            origin = "fichier fourni" if source else "synthétisé"
            if source is None:
                source = os.path.join(scratch, name + ".wav")
                write_wav(source, normalise(synthesise()))
            short = name[len("pad_"):]
            target = os.path.join(SOUND_DIR, short + ".ogg")
            # Une seule voie : le jeu n'atténue avec la distance que les sons mono.
            if source.lower().endswith(".ogg") and origin == "fichier fourni":
                # Un fichier fourni déjà au format du jeu est repris tel quel, sauf ses voies : un son stéréo est ramené
                # à une seule voie, sans quoi le jeu le jouerait partout au même volume, sans direction ni distance.
                short = name[len("pad_"):]
                target = os.path.join(SOUND_DIR, short + ".ogg")
                if channels(ffmpeg, source) > 1:
                    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis",
                                    "-q:a", "6", target], check=True)
                else:
                    shutil.copyfile(source, target)
                declared[name] = {"subtitle": "subtitles.orbitalis." + name, "sounds": ["orbitalis:pad/" + short]}
                print("%-14s %-15s %5.1f ko" % (name, "repris tel quel", os.path.getsize(target) / 1024.0))
                continue
            if origin != "fichier fourni":
                # Un son synthétisé est d'abord placé dans son lieu (échos), puis ramené à pleine échelle : les échos
                # l'ont allongé et affaibli.
                placed = os.path.join(scratch, name + "_placed.wav")
                subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-af", place_filter + ",highpass=f=24",
                                "-ac", "1", "-ar", str(RATE), "-c:a", "pcm_s16le", placed], check=True)
                with wave.open(placed, "rb") as f:
                    frames = struct.unpack("<%dh" % f.getnframes(), f.readframes(f.getnframes()))
                top = max(1, max(abs(x) for x in frames))
                source = os.path.join(scratch, name + "_final.wav")
                write_wav(source, [x / top * 0.93 for x in frames])
            if origin == "fichier fourni" and name in PAD_LEVELS:
                # Un fichier fourni est ramené à son niveau : d'un enregistrement à l'autre, les niveaux varient beaucoup.
                level, peak, gain = level_to(ffmpeg, source, target, PAD_LEVELS[name])
                declared[name] = {"subtitle": "subtitles.orbitalis." + name, "sounds": ["orbitalis:pad/" + short]}
                print("%-14s %-15s %5.1f ko  %6.1f LUFS  crête %5.1f dBFS  (gain %+.1f dB)" % (name, origin, os.path.getsize(target) / 1024.0, level, peak, gain))
                continue
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", source, "-af", "anull", "-ac", "1", "-ar", "44100",
                            "-c:a", "libvorbis", "-q:a", "5", target], check=True)
            declared[name] = {"subtitle": "subtitles.orbitalis." + name, "sounds": ["orbitalis:pad/" + short]}
            print("%-14s %-15s %5.1f ko" % (name, origin, os.path.getsize(target) / 1024.0))
        if not only:
            # Le grondement des moteurs en vol : une boucle sans couture, tirée du son d'allumage (ou du fichier
            # pad_engine fourni). Le jeu la répète tant que les moteurs brûlent.
            loop_target = os.path.join(SOUND_DIR, "engine_loop.ogg")
            loop_source = custom_file("pad_engine")
            if loop_source and loop_source.lower().endswith(".ogg") and channels(ffmpeg, loop_source) == 1:
                shutil.copyfile(loop_source, loop_target)
            else:
                make_loop(ffmpeg, loop_source or os.path.join(SOUND_DIR, "ignition.ogg"), loop_target, scratch)
            declared["pad_engine"] = {"subtitle": "subtitles.orbitalis.pad_engine", "sounds": ["orbitalis:pad/engine_loop"]}
            # Le déluge, en boucle sans couture : il joue tant que l'eau coule, de son ouverture à l'après-décollage.
            deluge_target = os.path.join(SOUND_DIR, "deluge_loop.ogg")
            make_loop(ffmpeg, custom_file("pad_deluge") or os.path.join(SOUND_DIR, "deluge.ogg"), deluge_target, scratch)
            declared["pad_deluge_loop"] = {"subtitle": "subtitles.orbitalis.pad_deluge_loop", "sounds": ["orbitalis:pad/deluge_loop"]}
        # Sons que le jeu joue moins fort que leur fichier : ils accompagnent le lancement, sans le couvrir.
        for name, volume in VOLUMES.items():
            if name in declared and (not only or name in only):
                declared[name]["sounds"] = [{"name": sound, "volume": volume} if isinstance(sound, str) else {**sound, "volume": volume}
                                            for sound in declared[name]["sounds"]]
        if not only:
            print("%-14s %-15s %5.1f ko" % ("pad_engine", "boucle", os.path.getsize(loop_target) / 1024.0))
    if not only:
        explosions(ffmpeg, declared)
    dedicated(ffmpeg, declared, only)
    with open(os.path.join(ASSETS, "sounds.json"), "w", newline="\n") as f:
        json.dump(declared, f, indent=2)
    print("%d sons écrits, sounds.json mis à jour." % len(declared))
