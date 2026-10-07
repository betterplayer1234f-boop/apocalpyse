"""Writes Zero Hour's JSON assets and data (models, blockstates, items, equipment, sounds, loot, recipes, tags,
damage types, lang). Run from the project root: python tools/gen_data.py"""
import json, os

NS = "zerohour"
ASSETS = "src/main/resources/assets/zerohour/"
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
for name in ("scorched_soil", "blast_shielding"):
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

# ---------------------------------------------------------------- fallout layers (like snow)
variants = {}
for layers in range(1, 9):
    height = layers * 2
    if layers == 8:
        model = {"parent": "minecraft:block/cube_all", "textures": {"all": ref("block/fallout")}}
    else:
        side_uv = [0, 16 - height, 16, 16]
        model = {
            "textures": {"particle": ref("block/fallout"), "texture": ref("block/fallout")},
            "elements": [{"from": [0, 0, 0], "to": [16, height, 16], "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                "north": {"uv": side_uv, "texture": "#texture", "cullface": "north"},
                "south": {"uv": side_uv, "texture": "#texture", "cullface": "south"},
                "west": {"uv": side_uv, "texture": "#texture", "cullface": "west"},
                "east": {"uv": side_uv, "texture": "#texture", "cullface": "east"}}}]}
        if layers == 1:
            model["parent"] = "minecraft:block/thin_block"
    write(ASSETS + "models/block/fallout_height%d.json" % height, model)
    variants["layers=%d" % layers] = {"model": ref("block/fallout_height%d" % height)}
write(ASSETS + "blockstates/fallout.json", {"variants": variants})
write(ASSETS + "items/fallout.json", model_item(ref("block/fallout_height2")))

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
flat = ("fallout_dust", "emergency_ration", "iodine_tablets", "anti_rad_serum", "antiviral",
        "gas_mask", "hazmat_suit", "hazmat_trousers", "hazmat_boots")
for name, parent in [(n, "generated") for n in flat] + [("flare_gun", "handheld")]:
    write(ASSETS + "models/item/%s.json" % name, {"parent": "minecraft:item/" + parent, "textures": {"layer0": ref("item/" + name)}})
    write(ASSETS + "items/%s.json" % name, model_item(ref("item/" + name)))

# The Geiger counter's needle moves through eight positions driven by zerohour:radiation.
for k in range(8):
    write(ASSETS + "models/item/geiger_counter_%d.json" % k, {"parent": "minecraft:item/generated", "textures": {"layer0": ref("item/geiger_counter_%d" % k)}})
write(ASSETS + "items/geiger_counter.json", {"model": {
    "type": "minecraft:range_dispatch",
    "property": ref("radiation"),
    "entries": [{"threshold": k / 8, "model": {"type": "minecraft:model", "model": ref("item/geiger_counter_%d" % k)}} for k in range(8)],
    "fallback": {"type": "minecraft:model", "model": ref("item/geiger_counter_0")}}})

# What the gas mask and hazmat suit look like when worn.
write(ASSETS + "equipment/hazmat.json", {"layers": {
    "humanoid": [{"texture": ref("hazmat")}],
    "humanoid_leggings": [{"texture": ref("hazmat")}]}})

# ---------------------------------------------------------------- sounds
subtitles = {
    "siren": "Air-raid siren wails",
    "eas_tone": "Emergency alert tone",
    "radio_static": "Radio crackles",
    "rumble": "The ground rumbles",
    "nuke_blast": "Nuclear blast roars",
    "geiger_click": "Geiger counter clicks",
    "flare_gun": "Signal flare fired",
    "shell_whistle": "Artillery shell screams in",
    "wind": "Wind howls",
    "jet_flyby": "Fighter jets scream past",
    "helicopter": "Helicopter rotors thud",
}
streamed = {"siren", "nuke_blast", "wind", "rumble", "shell_whistle", "jet_flyby", "helicopter"}
write(ASSETS + "sounds.json", {name: {"subtitle": "subtitles.zerohour." + name,
                                      "sounds": [{"name": ref(name), "stream": name in streamed}]} for name in subtitles})

# ---------------------------------------------------------------- damage types
damage = {
    "nuclear_blast": ("nuclearBlast", "burning", "%1$s was vaporized by the nuclear blast"),
    "shelter_breach": ("shelterBreach", "burning", "%1$s was crushed when their shelter caved in"),
    "radiation": ("radiation", "hurt", "%1$s died of radiation poisoning"),
    "infection": ("infection", "hurt", "%1$s turned into one of the infected"),
}
for name, (message, effects, _) in damage.items():
    write(DATA + "zerohour/damage_type/%s.json" % name, {"exhaustion": 0.0, "message_id": "zerohour." + message, "scaling": "never", "effects": effects})


def damage_tag(path, values):
    write(DATA + "minecraft/tags/damage_type/%s.json" % path, {"replace": False, "values": [ref(v) for v in values]})


everything = list(damage)
damage_tag("bypasses_armor", everything)
damage_tag("bypasses_shield", everything)
damage_tag("bypasses_enchantments", everything)
damage_tag("no_knockback", everything)
damage_tag("bypasses_effects", ["nuclear_blast", "radiation", "infection"])
damage_tag("bypasses_resistance", ["nuclear_blast", "radiation", "infection"])
damage_tag("bypasses_invulnerability", ["nuclear_blast"])
damage_tag("is_fire", ["nuclear_blast", "shelter_breach"])

# ---------------------------------------------------------------- lang
lang = {
    "itemGroup.zerohour": "Zero Hour",
    "block.zerohour.fallout": "Fallout",
    "block.zerohour.charred_log": "Charred Log",
    "block.zerohour.scorched_soil": "Scorched Soil",
    "block.zerohour.blast_shielding": "Blast Shielding",
    "block.zerohour.emergency_radio": "Emergency Radio",
    "item.zerohour.geiger_counter": "Geiger Counter",
    "item.zerohour.flare_gun": "Signal Flare Gun",
    "item.zerohour.iodine_tablets": "Iodine Tablets",
    "item.zerohour.anti_rad_serum": "Anti-Rad Serum",
    "item.zerohour.antiviral": "Antiviral Syringe",
    "item.zerohour.gas_mask": "Gas Mask",
    "item.zerohour.hazmat_suit": "Hazmat Suit",
    "item.zerohour.hazmat_trousers": "Hazmat Trousers",
    "item.zerohour.hazmat_boots": "Hazmat Boots",
    "item.zerohour.fallout_dust": "Radioactive Dust",
    "item.zerohour.emergency_ration": "Emergency Ration",
}
for name, (message, _, text) in damage.items():
    lang["death.attack.zerohour." + message] = text
    lang["death.attack.zerohour." + message + ".player"] = text
lang.update({"subtitles.zerohour." + k: v for k, v in subtitles.items()})
write(ASSETS + "lang/en_us.json", lang)

# ---------------------------------------------------------------- loot tables
def drop_self(name):
    return {"type": "minecraft:block", "random_sequence": ref("blocks/" + name), "pools": [{
        "rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": ref(name)}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


for name in ("charred_log", "scorched_soil", "blast_shielding", "emergency_radio"):
    write(DATA + "zerohour/loot_table/blocks/%s.json" % name, drop_self(name))

write(DATA + "zerohour/loot_table/blocks/fallout.json", {"type": "minecraft:block", "random_sequence": ref("blocks/fallout"), "pools": [{
    "rolls": 1, "bonus_rolls": 0,
    "entries": [{"type": "minecraft:alternatives", "children": [{
        "type": "minecraft:item", "name": ref("fallout_dust"),
        "conditions": [{"condition": "minecraft:block_state_property", "block": ref("fallout"), "properties": {"layers": str(n)}}],
        "functions": [{"function": "minecraft:set_count", "count": n, "add": False}]} for n in range(1, 9)]}]}]})

# ---------------------------------------------------------------- recipes
def shaped(name, pattern, key, result, count=1, category="misc"):
    write(DATA + "zerohour/recipe/%s.json" % name, {"type": "minecraft:crafting_shaped", "category": category,
                                                     "pattern": pattern, "key": key, "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1, category="misc"):
    write(DATA + "zerohour/recipe/%s.json" % name, {"type": "minecraft:crafting_shapeless", "category": category,
                                                     "ingredients": ingredients, "result": {"id": result, "count": count}})


shaped("emergency_radio", [" I ", "CRC", "CCC"], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, ref("emergency_radio"))
shaped("geiger_counter", [" G ", "CRC", " C "], {"G": "minecraft:glass_pane", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, ref("geiger_counter"), category="equipment")
shaped("flare_gun", ["II", "GR"], {"I": "minecraft:iron_ingot", "G": "minecraft:gunpowder", "R": "minecraft:red_dye"}, ref("flare_gun"), category="equipment")
shaped("blast_shielding", ["CIC", "IOI", "CIC"], {"C": "minecraft:gray_concrete", "I": "minecraft:iron_ingot", "O": "minecraft:obsidian"}, ref("blast_shielding"), 4, "building")
shaped("gas_mask", ["LLL", "GCG", " I "], {"L": "minecraft:leather", "G": "minecraft:glass_pane", "C": "minecraft:charcoal", "I": "minecraft:iron_ingot"}, ref("gas_mask"), category="equipment")
shaped("hazmat_suit", ["L L", "LSL", "YLY"], {"L": "minecraft:leather", "S": "minecraft:slime_ball", "Y": "minecraft:yellow_dye"}, ref("hazmat_suit"), category="equipment")
shaped("hazmat_trousers", ["LSL", "Y Y", "L L"], {"L": "minecraft:leather", "S": "minecraft:slime_ball", "Y": "minecraft:yellow_dye"}, ref("hazmat_trousers"), category="equipment")
shaped("hazmat_boots", ["L L", "S S"], {"L": "minecraft:leather", "S": "minecraft:slime_ball"}, ref("hazmat_boots"), category="equipment")
shapeless("iodine_tablets", ["minecraft:dried_kelp", "minecraft:dried_kelp", "minecraft:sugar"], ref("iodine_tablets"), 2)
shapeless("anti_rad_serum", ["minecraft:glass_bottle", "minecraft:blue_dye", "minecraft:iron_nugget", "minecraft:golden_carrot"], ref("anti_rad_serum"))
shapeless("antiviral", ["minecraft:glass_bottle", "minecraft:fermented_spider_eye", "minecraft:golden_apple", "minecraft:iron_nugget"], ref("antiviral"))
shapeless("emergency_ration", ["minecraft:bread", "minecraft:dried_kelp", "minecraft:baked_potato"], ref("emergency_ration"), 2)
shapeless("gray_dye_from_fallout_dust", [ref("fallout_dust"), ref("fallout_dust")], "minecraft:gray_dye")
shapeless("fallout", [ref("fallout_dust"), ref("fallout_dust"), ref("fallout_dust")], ref("fallout"), 2, "building")
shapeless("charcoal_from_charred_log", [ref("charred_log")], "minecraft:charcoal", 2)

# ---------------------------------------------------------------- block tags
def tag(path, values):
    write(DATA + "minecraft/tags/block/%s.json" % path, {"replace": False, "values": [ref(v) for v in values]})


tag("mineable/pickaxe", ["blast_shielding", "emergency_radio"])
tag("mineable/shovel", ["fallout", "scorched_soil"])
tag("mineable/axe", ["charred_log"])
tag("needs_iron_tool", ["blast_shielding"])
print("data generated")
