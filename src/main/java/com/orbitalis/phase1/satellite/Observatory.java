package com.orbitalis.phase1.satellite;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.network.ObservatoryPayload;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * L'observatoire de l'agence : ses satellites, pointés vers le ciel, découvrent les astres un à un.
 * <ol>
 * <li>Depuis une station de poursuite, on choisit un astre parmi ceux qu'on connaît (les premiers d'emblée, les
 * suivants révélés par les découvertes).</li>
 * <li>Les instruments en service l'observent : chaque satellite d'observation compte pour un, chaque télescope spatial
 * pour trois ; seul le télescope voit le ciel profond. L'observation n'avance que pendant la fenêtre de visibilité de
 * l'astre ({@link #visible}) : la Lune autour de sa pleine lune, Mars à son opposition, une comète à son passage, le
 * ciel profond à sa saison. Quand le temps d'observation est fait, l'astre est découvert, et l'agence prévenue.</li>
 * <li>Ses données sont alors à bord : on reçoit son image au sol depuis la station, si l'antenne voit le ciel ou
 * qu'un satellite relais transmet. L'image rapporte des points de recherche, et l'astre entre au catalogue, avec sa
 * fiche (et, pour une planète, comment s'y rendre).</li>
 * </ol>
 */
public final class Observatory {
    /** Intervalle entre deux avancées de l'observation, en ticks. */
    public static final int STEP = 20;
    /** Ce que compte un télescope spatial, face à un satellite d'observation. */
    public static final int TELESCOPE_POWER = 3;
    /** Coupoles de station qui comptent pour l'observatoire d'une agence, chacune pour un, au plus. */
    public static final int MAX_CUPOLAS = 2;

    /** Durée d'un jour du jeu, en ticks. */
    public static final int DAY = 24_000;

    /**
     * La fenêtre de visibilité d'un astre, en jours du jeu : elle revient tous les {@code period} jours, dure
     * {@code length} jours, et s'ouvre au jour {@code offset} de chaque période.
     */
    public record Window(int period, int length, int offset) {
    }

    private Observatory() {
    }

    /**
     * La fenêtre de visibilité de cet astre. La Lune suit ses vraies phases dans le jeu (la pleine lune est la phase
     * 0) : on l'observe de deux jours avant à deux jours après la pleine lune. Les lunes de Jupiter et de Saturne
     * suivent leur planète ; Mercure, trop près du Soleil, ne se montre que rarement ; le ciel profond a ses saisons.
     */
    public static Window window(CelestialBody body) {
        return switch (body) {
            case MOON -> new Window(8, 5, 6);
            case COMET -> new Window(24, 3, 10);
            case MARS -> new Window(12, 4, 3);
            case VENUS -> new Window(10, 4, 7);
            case MERCURY -> new Window(6, 2, 1);
            case JUPITER, EUROPA -> new Window(14, 6, 5);
            case SATURN, TITAN -> new Window(16, 6, 12);
            case PLEIADES -> new Window(16, 8, 0);
            case ORION_NEBULA -> new Window(16, 8, 8);
            case EAGLE_NEBULA -> new Window(16, 7, 4);
            case CRAB_PULSAR -> new Window(16, 7, 9);
            case ANDROMEDA -> new Window(16, 9, 13);
            case PROXIMA_B -> new Window(20, 6, 2);
            case SAGITTARIUS_A -> new Window(16, 6, 5);
            case TRAPPIST_1E -> new Window(18, 5, 7);
        };
    }

    /** Où l'on en est dans la période de cet astre, en ticks depuis l'ouverture de sa dernière fenêtre. */
    private static long phase(CelestialBody body, long dayTime) {
        Window window = window(body);
        long period = (long) window.period() * DAY;
        return Math.floorMod(dayTime - (long) window.offset() * DAY, period);
    }

    /** Vrai si cet astre est dans sa fenêtre de visibilité, à cette heure du jeu ({@code dayTime} du monde principal). */
    public static boolean visible(CelestialBody body, long dayTime) {
        return phase(body, dayTime) < (long) window(body).length() * DAY;
    }

    /** Ticks avant que la fenêtre de cet astre ne se ferme (s'il est visible) ou ne s'ouvre (sinon). */
    public static long untilChange(CelestialBody body, long dayTime) {
        Window window = window(body);
        long phase = phase(body, dayTime);
        long open = (long) window.length() * DAY;
        return phase < open ? open - phase : (long) window.period() * DAY - phase;
    }

    /** L'heure du jeu qui règle les fenêtres : celle du monde principal, avec ses jours et ses phases de la Lune. */
    public static long dayTime(MinecraftServer server) {
        return server.overworld().getOverworldClockTime();
    }

    /** Vrai si l'agence connaît cet astre : il est connu d'emblée, ou celui qui le révèle est découvert. */
    public static boolean listed(AgencySatellites satellites, CelestialBody body) {
        CelestialBody before = body.prerequisite();
        return before == null || satellites.isDiscovered(before);
    }

    /** Puissance d'observation que l'agence peut pointer sur cet astre : 0 si aucun de ses instruments ne le voit. */
    public static int power(AgencySatellites satellites, CelestialBody body, long now, boolean limited) {
        return power(satellites, body, now, limited, 0);
    }

    /**
     * La même, avec les coupoles des stations de l'agence : de là, l'équipage observe aussi les planètes et les lunes
     * (pas le ciel profond, hors de portée sans télescope).
     */
    public static int power(AgencySatellites satellites, CelestialBody body, long now, boolean limited, int cupolas) {
        int telescopes = satellites.active(SatelliteType.TELESCOPE, now, limited) * TELESCOPE_POWER;
        return body.deepSky() ? telescopes : telescopes + satellites.active(SatelliteType.OBSERVATION, now, limited) + cupolas;
    }

    /** Coupoles des stations de l'agence qui comptent pour l'observatoire : deux au plus. */
    public static int cupolas(MinecraftServer server, String agency) {
        return Math.min(MAX_CUPOLAS, com.orbitalis.phase2.station.StationLayout.count(server,
                com.orbitalis.phase2.station.StationManager.stationsOf(server, agency), com.orbitalis.phase2.station.ModuleType.CUPOLA));
    }

    /**
     * Une avancée de l'observation, sans tenir compte des fenêtres de visibilité (pour les essais). Rend l'astre
     * découvert, ou {@code null}.
     */
    public static CelestialBody advance(AgencySatellites satellites, long now, boolean limited) {
        return advance(satellites, now, limited, 0, -1L);
    }

    /** Une avancée de l'observation, appelée toutes les {@link #STEP} ticks ; {@code dayTime} négatif : sans fenêtre. */
    private static CelestialBody advance(AgencySatellites satellites, long now, boolean limited, int cupolas, long dayTime) {
        CelestialBody body = satellites.observing();
        if (body == null || satellites.isDiscovered(body)) {
            return null;
        }
        // Hors de sa fenêtre, l'astre n'est pas visible : les instruments restent pointés, et attendent.
        if (dayTime >= 0 && !visible(body, dayTime)) {
            return null;
        }
        int power = power(satellites, body, now, limited, cupolas);
        if (power <= 0) {
            return null;
        }
        satellites.addProgress(body, power * STEP);
        if (satellites.progress(body) >= body.effort()) {
            satellites.discover(body);
            return body;
        }
        return null;
    }

    /** Toutes les agences : leurs observations avancent ; une découverte est annoncée à leurs membres connectés. */
    public static void tick(MinecraftServer server, java.util.Map<String, AgencySatellites> agencies, long now, boolean limited) {
        boolean changed = false;
        for (var agency : agencies.entrySet()) {
            boolean busy = agency.getValue().observing() != null;
            CelestialBody found = busy ? advance(agency.getValue(), now, limited, cupolas(server, agency.getKey()), dayTime(server)) : null;
            changed |= busy;
            if (found != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (ResearchManager.agencyKey(player).equals(agency.getKey())) {
                        player.sendSystemMessage(Component.translatable("observatory.orbitalis.discovered", Component.translatable(found.nameKey()))
                                .withStyle(ChatFormatting.AQUA));
                    }
                }
            }
        }
        if (changed) {
            SatelliteManager.changed(server);
        }
    }

    /** Le joueur pointe les instruments de son agence sur cet astre. */
    public static void observe(ServerPlayer player, CelestialBody body) {
        MinecraftServer server = player.level().getServer();
        AgencySatellites satellites = SatelliteManager.of(server, ResearchManager.agencyKey(player));
        if (listed(satellites, body) && !satellites.isDiscovered(body)) {
            satellites.observe(body);
            SatelliteManager.changed(server);
        }
    }

    /** Le joueur reçoit au sol l'image d'un astre découvert : elle rapporte ses points, et l'astre entre au catalogue. */
    public static void receive(ServerPlayer player, BlockPos station, CelestialBody body) {
        MinecraftServer server = player.level().getServer();
        String agency = ResearchManager.agencyKey(player);
        AgencySatellites satellites = SatelliteManager.of(server, agency);
        if (!satellites.isDiscovered(body) || satellites.isImaged(body)) {
            return;
        }
        if (!SatelliteManager.isSkyClear(player.level(), station) && !satellites.hasRelay(SatelliteManager.now(server), SatelliteManager.limited())) {
            player.sendSystemMessage(Component.translatable("observatory.orbitalis.no_link").withStyle(ChatFormatting.GOLD));
            return;
        }
        satellites.image(body);
        SatelliteManager.changed(server);
        ResearchManager.forAgency(server, agency).addPoints(body.reward());
        ResearchManager.changed(server, agency);
        player.sendSystemMessage(Component.translatable("observatory.orbitalis.received", Component.translatable(body.nameKey()), body.reward())
                .withStyle(ChatFormatting.GREEN));
    }

    /** L'état de l'observatoire de l'agence de ce joueur, pour l'écran de la station. */
    public static ObservatoryPayload status(ServerPlayer player, BlockPos station) {
        MinecraftServer server = player.level().getServer();
        AgencySatellites satellites = SatelliteManager.of(server, ResearchManager.agencyKey(player));
        long now = SatelliteManager.now(server);
        boolean limited = SatelliteManager.limited();
        int cupolas = cupolas(server, ResearchManager.agencyKey(player));
        long dayTime = dayTime(server);
        List<ObservatoryPayload.Entry> entries = new ArrayList<>();
        for (CelestialBody body : CelestialBody.values()) {
            if (!listed(satellites, body)) {
                continue;
            }
            int state = satellites.isImaged(body) ? ObservatoryPayload.IMAGED : satellites.isDiscovered(body) ? ObservatoryPayload.DISCOVERED
                    : satellites.observing() == body ? ObservatoryPayload.OBSERVING : ObservatoryPayload.LISTED;
            int permille = state >= ObservatoryPayload.DISCOVERED ? 1000 : Math.min(999, satellites.progress(body) * 1000 / body.effort());
            entries.add(new ObservatoryPayload.Entry(body.ordinal(), state, permille, power(satellites, body, now, limited, cupolas),
                    visible(body, dayTime), (int) Math.min(Integer.MAX_VALUE, untilChange(body, dayTime))));
        }
        boolean link = SatelliteManager.isSkyClear(player.level(), station) || satellites.hasRelay(now, limited);
        return new ObservatoryPayload(station, satellites.active(SatelliteType.TELESCOPE, now, limited),
                satellites.active(SatelliteType.OBSERVATION, now, limited), link, entries);
    }
}
