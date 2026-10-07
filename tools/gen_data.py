"""Writes Last Sunset's JSON assets and data (models, blockstates, items, sounds, loot, recipes, tags, lang).
Run from the project root: python tools/gen_data.py"""
import json, os

NS = "lastsunset"
ASSETS = "src/main/resources/assets/lastsunset/"
DATA = "src/main/resources/data/"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def ref(path):
    return NS + ":" + path


def model_item(model):
    return {"model": {"type": "minecraft:model", "model": model}}


# ---------------------------------------------------------------- simple cube blocks
for name in ("scorched_soil", "solarite_ore", "flare_shielding"):
    write(ASSETS + "blockstates/%s.json" % name, {"variants": {"": {"model": ref("block/" + name)}}})
    write(ASSETS + "models/block/%s.json" % name, {"parent": "minecraft:block/cube_all", "textures": {"all": ref("block/" + name)}})
    write(ASSETS + "items/%s.json" % name, model_item(ref("block/" + name)))

# ---------------------------------------------------------------- charred log (axis)
write(ASSETS + "blockstates/charred_log.json", {"variants": {
    "axis=x": {"model": ref("block/charred_log_horizontal"), "x": 90, "y": 90},
    "axis=y": {"model": ref("block/charred_log")},
    "axis=z": {"model": ref("block/charred_log_horizontal"), "x": 90},
}})
for suffix, parent in (("", "cube_column"), ("_horizontal", "cube_column_horizontal")):
    write(ASSETS + "models/block/charred_log%s.json" % suffix, {
        "parent": "minecraft:block/" + parent,
        "textures": {"end": ref("block/charred_log_top"), "side": ref("block/charred_log")}})
write(ASSETS + "items/charred_log.json", model_item(ref("block/charred_log")))

# ---------------------------------------------------------------- ash layers (like snow)
variants = {}
for layers in range(1, 9):
    height = layers * 2
    if layers == 8:
        model = {"parent": "minecraft:block/cube_all", "textures": {"all": ref("block/ash_layer")}}
    else:
        side_uv = [0, 16 - height, 16, 16]
        model = {
            "textures": {"particle": ref("block/ash_layer"), "texture": ref("block/ash_layer")},
            "elements": [{"from": [0, 0, 0], "to": [16, height, 16], "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                "north": {"uv": side_uv, "texture": "#texture", "cullface": "north"},
                "south": {"uv": side_uv, "texture": "#texture", "cullface": "south"},
                "west": {"uv": side_uv, "texture": "#texture", "cullface": "west"},
                "east": {"uv": side_uv, "texture": "#texture", "cullface": "east"}}}]}
        if layers == 1:
            model["parent"] = "minecraft:block/thin_block"
    write(ASSETS + "models/block/ash_height%d.json" % height, model)
    variants["layers=%d" % layers] = {"model": ref("block/ash_height%d" % height)}
write(ASSETS + "blockstates/ash_layer.json", {"variants": variants})
write(ASSETS + "items/ash_layer.json", model_item(ref("block/ash_height2")))

# ---------------------------------------------------------------- emergency radio
metal = lambda uv: {"uv": uv, "texture": "#side"}
write(ASSETS + "models/block/emergency_radio.json", {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": ref("block/emergency_radio_side"),
        "front": ref("block/emergency_radio_front"),
        "side": ref("block/emergency_radio_side"),
        "top": ref("block/emergency_radio_top")},
    "elements": [
        {"from": [2, 0, 5], "to": [14, 9, 11], "faces": {
            "north": {"uv": [2, 7, 14, 16], "texture": "#front"},
            "south": {"uv": [2, 7, 14, 16], "texture": "#side"},
            "east": {"uv": [5, 7, 11, 16], "texture": "#side"},
            "west": {"uv": [5, 7, 11, 16], "texture": "#side"},
            "up": {"uv": [2, 5, 14, 11], "texture": "#top"},
            "down": {"uv": [2, 5, 14, 11], "texture": "#top", "cullface": "down"}}},
        {"from": [11.5, 9, 6], "to": [12.5, 16, 7], "faces": {
            f: metal([0, 0, 1, 7]) for f in ("north", "south", "east", "west", "up")}},
        {"from": [5, 9, 7.5], "to": [10, 10, 8.5], "faces": {
            f: metal([0, 0, 1, 5]) for f in ("north", "south", "east", "west", "up", "down")}},
    ]})
write(ASSETS + "blockstates/emergency_radio.json", {"variants": {
    "facing=north": {"model": ref("block/emergency_radio")},
    "facing=east": {"model": ref("block/emergency_radio"), "y": 90},
    "facing=south": {"model": ref("block/emergency_radio"), "y": 180},
    "facing=west": {"model": ref("block/emergency_radio"), "y": 270},
}})
write(ASSETS + "items/emergency_radio.json", model_item(ref("block/emergency_radio")))

# ---------------------------------------------------------------- flat items
for name, parent in (("solarite_shard", "generated"), ("ash", "generated"), ("emergency_ration", "generated"), ("flare_gun", "handheld")):
    write(ASSETS + "models/item/%s.json" % name, {"parent": "minecraft:item/" + parent, "textures": {"layer0": ref("item/" + name)}})
    write(ASSETS + "items/%s.json" % name, model_item(ref("item/" + name)))

# The heliometer's needle moves through eight positions driven by lastsunset:solar_flux.
for k in range(8):
    write(ASSETS + "models/item/heliometer_%d.json" % k, {"parent": "minecraft:item/generated", "textures": {"layer0": ref("item/heliometer_%d" % k)}})
write(ASSETS + "items/heliometer.json", {"model": {
    "type": "minecraft:range_dispatch",
    "property": ref("solar_flux"),
    "entries": [{"threshold": k / 8, "model": {"type": "minecraft:model", "model": ref("item/heliometer_%d" % k)}} for k in range(8)],
    "fallback": {"type": "minecraft:model", "model": ref("item/heliometer_0")}}})

# ---------------------------------------------------------------- sounds
subtitles = {
    "siren": "Distant siren wails",
    "eas_tone": "Emergency alert tone",
    "radio_static": "Radio crackles",
    "rumble": "The ground rumbles",
    "solar_roar": "The sun roars",
    "heliometer_click": "Heliometer clicks",
    "flare_gun": "Signal flare fired",
    "meteor_whistle": "Meteor screams overhead",
    "wind": "Hot wind howls",
}
streamed = {"siren", "solar_roar", "wind", "rumble", "meteor_whistle"}
write(ASSETS + "sounds.json", {name: {"subtitle": "subtitles.lastsunset." + name,
                                      "sounds": [{"name": ref(name), "stream": name in streamed}]} for name in subtitles})

# ---------------------------------------------------------------- lang
lang = {
    "itemGroup.lastsunset": "Last Sunset",
    "block.lastsunset.ash_layer": "Ash Layer",
    "block.lastsunset.charred_log": "Charred Log",
    "block.lastsunset.scorched_soil": "Scorched Soil",
    "block.lastsunset.solarite_ore": "Solarite Ore",
    "block.lastsunset.flare_shielding": "Flare Shielding",
    "block.lastsunset.emergency_radio": "Emergency Radio",
    "item.lastsunset.heliometer": "Heliometer",
    "item.lastsunset.flare_gun": "Signal Flare Gun",
    "item.lastsunset.solarite_shard": "Solarite Shard",
    "item.lastsunset.ash": "Ash",
    "item.lastsunset.emergency_ration": "Emergency Ration",
    "death.attack.lastsunset.solarFlare": "%1$s was vaporized by the dying sun",
    "death.attack.lastsunset.solarFlare.player": "%1$s was vaporized by the dying sun",
    "death.attack.lastsunset.firestorm": "%1$s walked into the firestorm",
    "death.attack.lastsunset.firestorm.player": "%1$s walked into the firestorm",
    "death.attack.lastsunset.shelterBreach": "%1$s was cooked alive when their shelter failed",
    "death.attack.lastsunset.shelterBreach.player": "%1$s was cooked alive when their shelter failed",
}
lang.update({"subtitles.lastsunset." + k: v for k, v in subtitles.items()})
write(ASSETS + "lang/en_us.json", lang)

# ---------------------------------------------------------------- loot tables
def drop_self(name):
    return {"type": "minecraft:block", "random_sequence": ref("blocks/" + name), "pools": [{
        "rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": ref(name)}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


for name in ("charred_log", "scorched_soil", "flare_shielding", "emergency_radio"):
    write(DATA + "lastsunset/loot_table/blocks/%s.json" % name, drop_self(name))

write(DATA + "lastsunset/loot_table/blocks/ash_layer.json", {"type": "minecraft:block", "random_sequence": ref("blocks/ash_layer"), "pools": [{
    "rolls": 1, "bonus_rolls": 0,
    "entries": [{"type": "minecraft:alternatives", "children": [{
        "type": "minecraft:item", "name": ref("ash"),
        "conditions": [{"condition": "minecraft:block_state_property", "block": ref("ash_layer"), "properties": {"layers": str(n)}}],
        "functions": [{"function": "minecraft:set_count", "count": n, "add": False}]} for n in range(1, 9)]}]}]})

write(DATA + "lastsunset/loot_table/blocks/solarite_ore.json", {"type": "minecraft:block", "random_sequence": ref("blocks/solarite_ore"), "pools": [{
    "rolls": 1, "bonus_rolls": 0,
    "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": ref("solarite_ore"), "conditions": [{"condition": "minecraft:match_tool", "predicate": {
            "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
        {"type": "minecraft:item", "name": ref("solarite_shard"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}, "add": False},
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
            {"function": "minecraft:explosion_decay"}]}]}]}]})

# ---------------------------------------------------------------- recipes
def shaped(name, pattern, key, result, count=1, category="misc"):
    write(DATA + "lastsunset/recipe/%s.json" % name, {"type": "minecraft:crafting_shaped", "category": category,
                                                       "pattern": pattern, "key": key, "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1, category="misc"):
    write(DATA + "lastsunset/recipe/%s.json" % name, {"type": "minecraft:crafting_shapeless", "category": category,
                                                       "ingredients": ingredients, "result": {"id": result, "count": count}})


shaped("emergency_radio", [" I ", "CRC", "CCC"], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, ref("emergency_radio"))
shaped("heliometer", [" G ", "CRC", " C "], {"G": "minecraft:glass_pane", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, ref("heliometer"), category="equipment")
shaped("flare_gun", ["II", "GR"], {"I": "minecraft:iron_ingot", "G": "minecraft:gunpowder", "R": "minecraft:red_dye"}, ref("flare_gun"), category="equipment")
shaped("flare_shielding", ["OIO", "ISI", "OIO"], {"O": "minecraft:obsidian", "I": "minecraft:iron_ingot", "S": ref("solarite_shard")}, ref("flare_shielding"), 4, "building")
shapeless("gray_dye_from_ash", [ref("ash"), ref("ash")], "minecraft:gray_dye")
shapeless("ash_layer", [ref("ash"), ref("ash"), ref("ash")], ref("ash_layer"), 2, "building")
shapeless("emergency_ration", ["minecraft:bread", "minecraft:dried_kelp", "minecraft:baked_potato"], ref("emergency_ration"), 2)
shapeless("charcoal_from_charred_log", [ref("charred_log")], "minecraft:charcoal", 2)

# ---------------------------------------------------------------- tags
def tag(path, values):
    write(DATA + "minecraft/tags/block/%s.json" % path, {"replace": False, "values": [ref(v) for v in values]})


tag("mineable/pickaxe", ["solarite_ore", "flare_shielding", "emergency_radio"])
tag("mineable/shovel", ["ash_layer", "scorched_soil"])
tag("mineable/axe", ["charred_log"])
tag("needs_iron_tool", ["solarite_ore", "flare_shielding"])
print("data generated")
