# ☀ Last Sunset — Fabric 1.21.11

*The sun is dying. Nobody tells you when. You have a radio, a heliometer, and whatever you can dig.*

A multiplayer end-of-the-world mod. The moment the server starts, the sun begins to change. There is no countdown: just signs, getting worse, until a solar superflare scorches the surface. Anyone the sunlight can reach is vaporized. Everyone else holds their breath underground, then climbs out into a burned world on Day One.

## Install

1. Install **Fabric Loader** (0.17+) for **Minecraft 1.21.11** and put **Fabric API** in `mods/`.
2. Put `build/libs/last-sunset-1.0.0.jar` in `mods/` on the **server and every client**.
3. Start the server. That's it: it has already begun.

By default the build-up lasts **60 minutes of play** (the clock only runs while someone is online). Operators can change that with `/apocalypse config duration <minutes>`.

## No countdown, only signs

| Stage | What you notice |
|---|---|
| **Omens** | The sun looks a little too big and warm. A blood-tinged moon. Faint tremors. Animals bolt for no reason. Shooting stars at night. The radio says there is "no cause for alarm". |
| **Evacuation** | A siren wails somewhere, then the **Emergency Alert System** tone. Ash begins to fall and settle. Meteors scream overhead and leave smoking craters with **glowing solarite** in their cores. **Supply crates** fall on columns of smoke. Plants wither. |
| **Collapse** | Time races and the sun **freezes low in the western sky: the last sunset**. It's huge, red and pulsing. The light turns orange, the sky blood-red. Dry lightning, fissures glowing with magma, **boiling oceans**, ash piling up. Music fades. Standing in the sun drains you. |
| **Final minutes** | Constant tremors, meteor storms, burning skin in the open. The radio's *last transmission*. Your heartbeat quickens. Then eight seconds of complete silence. |
| **The flare** | A rising whine. The sun swells until the sky is white. **The roar.** The exposed are lifted, set ablaze and turned to ash, leaving a burned shadow and a skull. Shelters shake, dust falls, lights stutter. |
| **Aftermath** | Night and ash. The surface is a **firestorm** for 5 minutes. The land becomes scorched earth: **charred logs** with embers still glowing, **scorched soil**, fused-glass sand, drifts of **ash**. The radio crackles with broken, glitching voices. |
| **Day One** | A sunrise. The fallen rise from the ashes next to a survivor. A quieter, ashen world under a weakened red sun. |

How do you know how close it is? Watch the sky, listen to the radio, and **check your heliometer**: its needle creeps from green to red, and it clicks like a Geiger counter, faster and faster.

## Custom content

**Your survival kit** (given once to every player on first join): Emergency Radio, Heliometer, 2 Emergency Rations.

| | | Recipe |
|---|---|---|
| **Emergency Radio** (block) | Carry it, or stand within 12 blocks of a placed one, to hear broadcasts. Blinking LED, hisses with static. Right-click (placed or in hand) to replay the last message. | iron on top, copper–redstone–copper, three copper |
| **Heliometer** | Brass solar-flux meter. Animated needle, Geiger clicks, right-click for a reading ("Solar flux 412% — unstable"). After the flare it reads surface temperature. | glass pane / copper–redstone–copper / copper |
| **Signal Flare Gun** | Fires a red flare that climbs high, bursts and hangs in the sky under a parachute. Mark your bunker for friends. 16 shots. | iron, iron / gunpowder, red dye |
| **Solarite Ore → Solarite Shard** | Glowing, pulsing ore found in meteorite cores. | mine with iron+ (fortune works) |
| **Flare Shielding** (block) | Obsidian laced with solarite. Worth **8 blocks of stone** in a shelter roof. | obsidian corners, iron sides, solarite centre → 4 |
| **Ash Layer** + **Ash** | Falls and piles up like snow that never melts. Ash → gray dye, or 3 ash → 2 layers. | |
| **Charred Log**, **Scorched Soil** | What the flare leaves behind. Charred log → 2 charcoal. | |
| **Emergency Ration** | Tinned food (7 hunger). Found in supply crates. | bread + dried kelp + baked potato → 2 |

**Sounds** (all synthesized for the mod): civil-defence siren, the real EAS header bursts and two-tone alert, radio static, earthquake rumble, the flare's roar, meteor whistle, heliometer clicks, flare-gun shot, hot wind.

## Surviving: the shelter rule

> **If sunlight can reach you, the flare can too.**

1. **Seal it.** The sky light at your head must be **0**. A shaft straight down lets the flare straight in. Doors, glass and leaves let light through.
2. **Bury it.** You need **30 rock-meters** overhead: stone/deepslate = 1 per block, obsidian = 4, **flare shielding = 8**, dirt/sand = 0.25, water = 0.5.

The small **shelter line** (top-left) shows your rating live. `/shelter` gives a detailed report. A sealed but thin shelter lets some heat through.

**Multiplayer:** hold **Tab** to see who is safe, at risk, exposed or fallen. Anyone who dies during the flare or firestorm spectates until dawn, then is revived.

## The HUD stays out of your way

Nothing covers the middle of the screen, and there's no timer anywhere. You get one shelter line, a small radio box (only if you have a radio), a survivor list behind Tab, and heat glowing at the edges of your vision.

## Operator commands

| Command | |
|---|---|
| `/apocalypse status` | Phase, hidden time remaining, settings |
| `/apocalypse config duration <minutes>` | Length of the build-up (keeps current progress) |
| `/apocalypse config autostart <true/false>` | Start by itself on a new world (default true) |
| `/apocalypse start [minutes]` / `stop` | Restart or cancel (`stop` also turns off autostart) |
| `/apocalypse pause` / `resume` / `skip` / `time <seconds>` / `impact` | Direct the show |
| `/apocalypse meteor` / `supplydrop` | Drop one near you |
| `/apocalypse config shielding / aftermath / griefing / spectate / revive / meteors` | Tuning |
| `/shelter` | Anyone: how protected am I right here? |

## Building from source

Requires JDK 21.

```
gradlew build              # -> build/libs/last-sunset-1.0.0.jar
gradlew runClient          # play in a dev client
gradlew runClientGameTest  # plays the whole apocalypse automatically, screenshots in build/run/clientGameTest/screenshots
```

Art and audio are generated by scripts in `tools/`: `gen_pixel_art.py` and `gen_textures.py` (pure Python), `gen_sounds.py` (needs `pip install numpy soundfile`), and `gen_data.py` (models, recipes, loot tables, lang).
