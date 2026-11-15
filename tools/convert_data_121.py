"""
Convertit les données d'Orbitalis du format de Minecraft 26.2 (branche main) vers celui de 1.21.1.

Entrées (copiées de main) : src/main/resources/data et src/generated/resources/data.
Sorties :
  - common/src/main/resources/data                  les données communes aux deux chargeurs ;
  - neoforge/src/main/resources/data                ce qui n'existe que sous NeoForge (modificateurs de biomes) ;
  - common/src/main/resources/assets/orbitalis/dimension_visuals/<dimension>.json
        tout ce que 26.2 disait de l'apparence d'une dimension (ciel, brouillard, nuages, astres, lumière) : le rendu
        du client 1.21.1 le lit, faute d'« attributs » d'environnement dans le jeu ;
  - common/src/main/resources/assets/orbitalis/models/item/*.json
        les modèles d'objets que 26.2 décrivait dans assets/orbitalis/items.

Le script se relance sans risque : il réécrit ses sorties à chaque fois.
"""
import io
import json
import math
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = [os.path.join(ROOT, "src", "main", "resources", "data"), os.path.join(ROOT, "src", "generated", "resources", "data")]
COMMON_DATA = os.path.join(ROOT, "common", "src", "main", "resources", "data")
NEOFORGE_DATA = os.path.join(ROOT, "neoforge", "src", "main", "resources", "data")
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "orbitalis")

# Ce qui n'a pas d'équivalent en 1.21.1 : l'horloge du monde devient l'heure fixe des dimensions.
SKIPPED_DIRS = {os.path.join("orbitalis", "world_clock")}
NEOFORGE_ONLY_DIRS = {os.path.join("orbitalis", "neoforge")}

INTERSTELLAR_FABRIC = {"condition": "orbitalis:interstellar"}


def load(path):
    with io.open(path, encoding="utf-8") as f:
        return json.load(f)


def save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def color(value, default=0):
    """Une couleur « #rrggbb » ou « #aarrggbb » de 26.2, en entier comme l'attend 1.21.1."""
    if value is None:
        return default
    if isinstance(value, int):
        return value
    return int(value.lstrip("#"), 16)


# ----------------------------------------------------------------------------------------------------------------------
# Recettes
# ----------------------------------------------------------------------------------------------------------------------

def ingredient(value):
    """26.2 écrit un ingrédient comme « orbitalis:x », « #c:tag » ou une liste ; 1.21.1 attend {item} / {tag}."""
    if isinstance(value, str):
        return {"tag": value[1:]} if value.startswith("#") else {"item": value}
    if isinstance(value, list):
        return [ingredient(v) for v in value]
    return value


def recipe(data):
    if "key" in data:
        data["key"] = {k: ingredient(v) for k, v in data["key"].items()}
    if "ingredients" in data:
        out = []
        for entry in data["ingredients"]:
            if isinstance(entry, dict) and "ingredient" in entry:
                entry = dict(entry, ingredient=ingredient(entry["ingredient"]))
                out.append(entry)
            else:
                out.append(ingredient(entry))
        data["ingredients"] = out
    for field in ("ingredient", "base", "addition", "template"):
        if field in data:
            data[field] = ingredient(data[field])
    if isinstance(data.get("item_input"), dict) and "ingredient" in data["item_input"]:
        data["item_input"] = dict(data["item_input"], ingredient=ingredient(data["item_input"]["ingredient"]))
    return data


def conditions(data):
    """Les conditions de NeoForge, doublées pour Fabric : chaque chargeur ignore la clé de l'autre."""
    neo = data.get("neoforge:conditions")
    if not neo:
        return data
    fabric = []
    for condition in neo:
        if condition.get("type") == "orbitalis:interstellar":
            fabric.append(INTERSTELLAR_FABRIC)
        else:
            sys.exit("condition sans équivalent Fabric : %r" % condition)
    out = {"neoforge:conditions": neo, "fabric:load_conditions": fabric}
    out.update({k: v for k, v in data.items() if k != "neoforge:conditions"})
    return out


# ----------------------------------------------------------------------------------------------------------------------
# Dimensions et biomes
# ----------------------------------------------------------------------------------------------------------------------

def time_of_day(time):
    """L'angle du soleil (de 0 à 1) d'une heure du jour, comme le calcule 1.21.1."""
    d0 = (time / 24000.0 - 0.25) % 1.0
    d1 = 0.5 - math.cos(d0 * math.pi) / 2.0
    return (d0 * 2.0 + d1) / 3.0


def fixed_time(sun_angle):
    """L'heure fixe qui place le soleil de 1.21.1 à l'angle que donnait 26.2 (0 : midi, 180 : minuit)."""
    target = (sun_angle / 360.0) % 1.0
    best, best_error = 6000, 2.0
    for time in range(24000):
        error = abs(time_of_day(time) - target)
        error = min(error, 1.0 - error)
        if error < best_error:
            best, best_error = time, error
    return best


VISUALS = {}


def dimension_type(name, data):
    attributes = data.get("attributes", {})
    VISUALS.setdefault(name, {})["dimension"] = attributes
    VISUALS[name]["ambient_light"] = data.get("ambient_light", 0.0)
    bed = attributes.get("minecraft:gameplay/bed_rule", {})
    out = {
        "ultrawarm": bool(attributes.get("minecraft:gameplay/water_evaporates", False)),
        "natural": False,
        "piglin_safe": False,
        # Les lits ne font rien hors de la Terre : un mixin empêche l'explosion du jeu (voir BedBlockMixin).
        "bed_works": bed.get("can_sleep", "always") != "never",
        "respawn_anchor_works": bool(attributes.get("minecraft:gameplay/respawn_anchor_works", False)),
        "has_raids": False,
        "has_skylight": data.get("has_skylight", True),
        "has_ceiling": data.get("has_ceiling", False),
        "coordinate_scale": data.get("coordinate_scale", 1.0),
        "ambient_light": data.get("ambient_light", 0.0),
        "min_y": data["min_y"],
        "height": data["height"],
        "logical_height": data["logical_height"],
        "infiniburn": data.get("infiniburn", "#minecraft:infiniburn_overworld"),
        "effects": attributes.get("neoforge:custom_skybox", "minecraft:" + data.get("skybox", "overworld")),
        "monster_spawn_light_level": data.get("monster_spawn_light_level", 0),
        "monster_spawn_block_light_limit": data.get("monster_spawn_block_light_limit", 0),
    }
    if data.get("has_fixed_time"):
        out["fixed_time"] = fixed_time(attributes.get("minecraft:visual/sun_angle", 0.0))
    return out


def biome(name, data, dimension_attributes):
    attributes = dict(dimension_attributes)
    attributes.update(data.get("attributes", {}))
    VISUALS.setdefault("biome/" + name, {})["biome"] = data.get("attributes", {})
    effects = dict(data.get("effects", {}))
    out_effects = {
        "fog_color": color(attributes.get("minecraft:visual/fog_color"), 0xC0D8FF),
        "sky_color": color(attributes.get("minecraft:visual/sky_color"), 0x78A7FF),
        "water_color": color(effects.pop("water_color", None), 0x3F76E4),
        "water_fog_color": color(attributes.get("minecraft:visual/water_fog_color"), 0x050533),
    }
    for key, value in effects.items():
        out_effects[key] = color(value) if key.endswith("_color") else value
    out = {
        "has_precipitation": data.get("has_precipitation", False),
        "temperature": data.get("temperature", 0.5),
        "downfall": data.get("downfall", 0.0),
        "effects": out_effects,
        "spawners": data.get("spawners", {}),
        "spawn_costs": data.get("spawn_costs", {}),
        "carvers": data["carvers"] if isinstance(data.get("carvers"), dict)
        else ({"air": data["carvers"]} if data.get("carvers") else {}),
        "features": data.get("features", []),
    }
    if "temperature_modifier" in data:
        out["temperature_modifier"] = data["temperature_modifier"]
    return out


# ----------------------------------------------------------------------------------------------------------------------
# Génération du monde
# ----------------------------------------------------------------------------------------------------------------------

def trapezoid(node):
    """
    L'entier « trapezoid » de 26.2 (la somme de deux tirages uniformes) n'existe pas en 1.21.1 : la même loi, exactement,
    en liste pondérée.
    """
    low, high, plateau = node["min"], node["max"], node.get("plateau", 0)
    side = (high - low - plateau) // 2
    other = high - low - side
    weights = {}
    for a in range(other + 1):
        for b in range(side + 1):
            weights[low + a + b] = weights.get(low + a + b, 0) + 1
    return {"type": "minecraft:weighted_list",
            "distribution": [{"data": value, "weight": weight} for value, weight in sorted(weights.items())]}


def worldgen(node):
    if isinstance(node, list):
        return [worldgen(v) for v in node]
    if not isinstance(node, dict):
        return node
    # (Le « trapezoid » des hauteurs, avec min_inclusive et max_inclusive, existe aussi en 1.21.1 : on le garde.)
    if node.get("type") == "minecraft:trapezoid" and "min" in node and "max" in node             and all(isinstance(node.get(k, 0), int) for k in ("min", "max", "plateau")):
        return trapezoid(node)
    if node.get("type") == "minecraft:block_blob":
        node = dict(node, type="orbitalis:block_blob")
    return {k: worldgen(v) for k, v in node.items()}


def noise_settings(data):
    """
    26.2 remplace la densité initiale du routeur par « preliminary_surface_level » (une recherche de la surface sur cette
    densité, moins le seuil du sol) : 1.21.1 veut la densité elle-même.
    """
    router = data.get("noise_router", {})
    surface = router.pop("preliminary_surface_level", None)
    if surface is not None and "initial_density_without_jaggedness" not in router:
        if not (isinstance(surface, dict) and surface.get("type") == "minecraft:find_top_surface"):
            sys.exit("preliminary_surface_level inattendu : %s" % json.dumps(surface)[:200])
        density = surface["density"]
        if isinstance(density, dict) and density.get("type") == "minecraft:add" and density.get("argument1") == -0.390625:
            density = density["argument2"]
        else:
            density = {"type": "minecraft:add", "argument1": 0.390625, "argument2": density}
        router["initial_density_without_jaggedness"] = density
    return data


# ----------------------------------------------------------------------------------------------------------------------
# Tout ensemble
# ----------------------------------------------------------------------------------------------------------------------

def biome_dimensions(sources):
    """Pour chaque biome du mod, les attributs de la dimension qui l'utilise (le ciel venait souvent de là)."""
    found = {}
    for source in sources:
        folder = os.path.join(source, "orbitalis", "dimension")
        if not os.path.isdir(folder):
            continue
        for f in os.listdir(folder):
            dim = load(os.path.join(folder, f))
            type_path = os.path.join(source, "orbitalis", "dimension_type", dim["type"].split(":")[1] + ".json")
            attributes = load(type_path).get("attributes", {}) if os.path.exists(type_path) else {}
            biomes = json.dumps(dim["generator"])
            for value in set(part.strip('"') for part in biomes.replace(",", " ").replace("}", " ").replace("]", " ").split()):
                if value.startswith("orbitalis:"):
                    found.setdefault(value.split(":")[1], attributes)
    return found


def convert():
    for out in (COMMON_DATA, os.path.join(NEOFORGE_DATA, "orbitalis", "neoforge")):
        if os.path.isdir(out):
            shutil.rmtree(out)
    visuals = os.path.join(ASSETS, "dimension_visuals")
    if os.path.isdir(visuals):
        shutil.rmtree(visuals)
    biome_attributes = biome_dimensions(SOURCES)
    count = 0
    for source in SOURCES:
        for folder, _, files in os.walk(source):
            rel_dir = os.path.relpath(folder, source)
            if any(rel_dir == d or rel_dir.startswith(d + os.sep) for d in SKIPPED_DIRS):
                continue
            target_root = NEOFORGE_DATA if any(rel_dir == d or rel_dir.startswith(d + os.sep) for d in NEOFORGE_ONLY_DIRS) else COMMON_DATA
            for f in files:
                path = os.path.join(folder, f)
                target = os.path.join(target_root, rel_dir, f)
                if not f.endswith(".json"):
                    os.makedirs(os.path.dirname(target), exist_ok=True)
                    shutil.copyfile(path, target)
                    continue
                data = load(path)
                parts = rel_dir.replace("\\", "/").split("/")
                name = f[:-5]
                if parts[:2] == ["orbitalis", "recipe"]:
                    data = recipe(data)
                elif parts[:2] == ["orbitalis", "dimension_type"]:
                    data = dimension_type(name, data)
                elif parts[:3] == ["orbitalis", "worldgen", "biome"]:
                    data = biome(name, data, biome_attributes.get(name, {}))
                elif parts[:3] == ["orbitalis", "worldgen", "noise_settings"]:
                    data = noise_settings(data)
                if parts[:2] == ["orbitalis", "worldgen"]:
                    data = worldgen(data)
                data = conditions(data)
                save(target, data)
                count += 1
    for name, visual in VISUALS.items():
        save(os.path.join(visuals, name + ".json"), visual)
    print("données converties :", count, "; apparences :", len(VISUALS))


def item_models():
    """26.2 décrit chaque objet dans items/ ; 1.21.1 lit models/item/<objet>.json."""
    items = os.path.join(ASSETS, "items")
    if not os.path.isdir(items):
        return
    made = 0
    for f in os.listdir(items):
        definition = load(os.path.join(items, f))["model"]
        if definition.get("type") != "minecraft:model" or "tints" in definition:
            sys.exit("définition d'objet à convertir à la main : " + f)
        model = definition["model"]
        name = f[:-5]
        target = os.path.join(ASSETS, "models", "item", f)
        if model == "orbitalis:item/" + name:
            if not os.path.exists(target):
                sys.exit("modèle d'objet manquant : " + target)
            continue
        save(target, {"parent": model})
        made += 1
    shutil.rmtree(items)
    print("modèles d'objets créés :", made)


def emissive_elements():
    """
    Les faces lumineuses (« light_emission » de 26.2) : NeoForge 1.21.1 les lit dans « neoforge_data » ; le jeu de base et
    Fabric ignorent la clé, le client Fabric les rend lumineuses à part.
    """
    changed = 0
    for folder, _, files in os.walk(os.path.join(ASSETS, "models")):
        for f in files:
            if not f.endswith(".json"):
                continue
            path = os.path.join(folder, f)
            data = load(path)
            touched = False
            for element in data.get("elements", []):
                light = element.get("light_emission")
                if light and "neoforge_data" not in element:
                    element["neoforge_data"] = {"block_light": light, "sky_light": light}
                    touched = True
            if touched:
                save(path, data)
                changed += 1
    print("modèles à faces lumineuses :", changed)


if __name__ == "__main__":
    convert()
    item_models()
    emissive_elements()
