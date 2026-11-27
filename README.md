# LegendaryWeaponsVL

Standalone legendary-weapons plugin by Vaileks. Includes eleven legendary items and four epic items. Java 21+ is required; other plugins are not required.

Custom models, animated vines, tooltip styles and sounds require the compatible server resource pack, distributed through the Discord link below. The resource pack is not bundled in the JAR.

Supported server software: **Paper, Purpur, Spigot and Leaf**. Supported Minecraft releases: **1.21.1 and 1.21.3–1.21.11, plus Paper 26.3**, where that server software provides a build. Minecraft 26.3 requires Java 25+. Leaf support starts at 1.21.4. Folia is not supported.

Minecraft 1.21.1 uses legacy CustomModelData instead of item-model components and has standard tooltip backgrounds. It needs a matching legacy resource pack; a 1.21.4+ pack cannot be assumed to work on an older client.

When moving an existing server from Paper to Spigot, first run this version on Paper and load the gauntlets once, or withdraw their stored totems before changing cores. Older Paper-native totem data is converted to portable storage when inventory items are refreshed. New storage works on both cores.

## Commands

- `/lwv` — open the admin item menu.
- `/lwv give <player> <life|judge|rail|glove|ludo|flaming|shield|katana|reaper|artemis|tv|glitch|midas|emerald|excalibur> [amount]` — give an item.
- `/lwv reload` — reload settings and language.
- `/lwv resetcd <player>` — reset cooldowns.

Full command: `/legendaryweaponsvl`. Permission: `legendaryweaponsvl.admin` (legacy `runeslegendaryv.admin` is also accepted); operators have access by default.

## Configuration

- `plugins/LegendaryWeaponsVL/config.yml` — general settings and `language: ru` or `language: en`.
- `plugins/LegendaryWeaponsVL/items/` — damage, cooldowns, abilities, models and sounds for each item.
- `plugins/LegendaryWeaponsVL/lang/` — messages, menus, names and descriptions.

Apply changes with `/lwv reload`. Custom text in an item configuration overrides the selected translation; remove that field to use the language file. Legacy default Russian text migrates automatically. Updating item text preserves models, attributes, stored totems and cooldowns.

There are no pedestal recipes or ban integrations. Adventure text libraries are bundled for Spigot; no additional plugin is needed.

For legacy resource-pack authors: automatically assigned model IDs are `1000000 + floorMod(modelKey.hashCode(), 1000000)`, using the Java string hash. Explicit `custom_model_data` values are preserved.

## Build

Requires Python 3, JDK 21+ and Spigot API 1.21.1 and Paper API 1.21.4 with Adventure, JOML, Guava, JetBrains/JSPECIFY and BungeeCord Chat dependencies in the local Maven cache (`~/.m2/repository`). Optional environment variables: `JAVA_HOME`, `SERVER_API`, `PAPER_ADAPTER_API` and `ADVENTURE_VERSION` (default `4.20.0`).

```sh
python3 build.py
```

Output: `LegendaryWeaponsVL.jar`.

`tests/StandaloneSmoke.java`, `tests/ExpansionAudit.java` and `tests/LocalizationAudit.java` check item mechanics, cooldowns, totem serialization and RU/EN localization on a disposable Paper test server. The test helper shuts down the server and must not be installed on a production server. `tests/CompatibilityAudit.java` additionally verifies the Paper/Spigot adapter and storage format. `tests/KatanaAudit.java` checks blink distance, charge regeneration, cancelled teleports and collision safety.

Compatibility checks pass on Paper 1.21.1 build 133, Paper 1.21.3 build 83, Spigot 1.21.3 and Leaf 1.21.11 build 183. The fifteen-item build passes 820 checks on Paper 1.21.11 and 820 on Paper 26.3 build 256, including the katana, scythe and live homing arrows. Tests include native and portable totem storage, migration, combat adapters, railgun release and configuration reload.

## Links

- [Modrinth](https://modrinth.com/plugin/legendaryweaponsvaileks)
- [Resource pack and support](https://discord.gg/ssXctkBkQD)
- [Source](https://github.com/Vaileks/LegendaryWeaponsVL)

Vine resource-pack additions: run `python3 tools/export_plant_vines.py <model.bbmodel> SwordOfLife-RP.zip` using the supplied eight-section model. Merge the archive’s `assets/` folder into the compatible pack and reload the pack on clients. The growth/wrapping timing and movement are unchanged; the plugin selects the eight matching meshes. The model and texture are not embedded in the JAR.

## Items and rarity

Legendary items have a gold rarity line: Sword of Life, Judge Sword, Railgun, Gauntlet of Fortitude, Lucky Sword (Лудо-меч), Dragon Katana, Reaper Scythe, Artemis Bow, Midas Sword, Emerald Blade and Excalibur. Epic items have a purple rarity line: Flaming Crossbow, Netherite Shield, Television and Glitch. All fifteen are available in `/lwv` and the give command, with separate item files and RU/EN text.

- Flaming Crossbow ignites its arrows, with CustomModelData 6000.
- Netherite Shield provides +4 maximum health, +4 armor and +2 armor toughness in the off hand only. It blocks normally.
- Lucky Sword rolls one of eleven powers on right-click. Its model changes during Jackpot and returns to normal when the effect ends. Every power can be disabled or tuned independently in `items/GambleSword.yml`. Timed powers reserve their duration plus cooldown so reconnects cannot reset the roll.

| Lucky Sword power | Behavior |
| --- | --- |
| Light Mace | Launch upward; protect the landing from fall damage. |
| Dragon Katana | Teleport to the safe surface ahead within loaded terrain. |
| Mirror Sword | Haste II and Glowing. |
| Spider Sword | Temporary cobweb rings that restore their original blocks. |
| Excalibur | Block up to six qualifying attacks within the effect window. |
| Shadow Blade | Hide the player and equipment; a damaging hit breaks concealment. |
| Shrinker | Temporarily halve the player scale without overwriting other scale modifiers. |
| Frost Sword | Charged melee hits accumulate frost, slow and eventually root the target. |
| Mjolnir | Charged melee hits call lightning; right-click fires a spinning visual blade that calls lightning on impact. |
| Reaper Scythe | Arm the next charged hit to steal health and potion effects. |
| Jackpot | Temporary Sharpness XVIII, Speed III, Strength III and protection from player attacks. |

Only Paper API is used. Visual projectiles never drop equipment. Shutdown/reload restores temporary blocks, visibility, scale, effects and Lucky Sword appearance. No pedestal, framework, punishment, packet plugin integration is included.

## Source layout

`weapon/` owns each item's mechanics; `listener/` routes shared combat and equipment events; `config/` reads settings and translations; `support/` provides cooldown feedback, presentation and damage protection. Source uses one import per line and consistent Java formatting. Tests live separately in `tests/` and are never included in the published plugin.

Dragon Katana retains its original 14-block right-click blink, +10% main-hand movement speed, Sharpness XI and 17-second recharge. Ordinary terrain can be crossed; unbreakable blocks, world borders and collision safety are respected. Tune its rechargeable uses and range in `items/DragonKatana.yml`.

## Reaper Scythe and Artemis Bow

- Reaper Scythe steals up to 6 HP and transfers the victim’s active potion effects on a fully recovered melee hit. Cooldown: 55 seconds. Damage uses the shared native damage pipeline, consumes absorption first and respects cancelled damage, totems and gauntlet protection. Healing is capped by the wielder’s current maximum health; no health attribute is transferred. Tune `hp_steal` and `cooldown_ticks` in `items/ReaperScythe.yml`.
- Artemis Bow keeps Power XII and Flame II. Each shot has a 1-in-3 homing chance; impacts independently roll lightning (1 in 5), fire (1 in 5) and explosion (1 in 16, power 3.75). Homing favors enemy players and excludes the owner, their vehicle, creative/spectator players and scoreboard teammates with friendly fire disabled.
- Shift + left-click with Artemis toggles all area effects (lightning, fire and explosion); homing remains available. `break_blocks` additionally controls explosion block damage. Ignition and explosions use Bukkit events so protection plugins can cancel them. There is no added ability cooldown on Artemis.
- Configure chances, range, fire radius and area effects in `items/ArtemisBow.yml`. A weight of `0` disables that random effect; `1` always triggers it. Homing stops after impact, expiry, owner disconnect or plugin reload. Existing resource-pack model paths are retained.

`tests/NewWeaponsAudit.java` checks scythe theft, cooldowns, cancelled damage, absorption, potion transfer, Artemis area-mode switching, native impact events, teammate exclusion and actual homing flight on a disposable server.

Existing `plugins/RunesLegendaryV` settings are moved automatically when `plugins/LegendaryWeaponsVL` does not exist. Existing item IDs, stored totems and attribute keys retain their original namespace. `/rlv` and `/runeslegendaryv` remain command aliases. Replace the previous JAR rather than installing both copies.


## Epic maces

- **Television** (`tv`): Wind Burst II, Density IV, with a 5% curse chance on player attack attempts, including rapid clicks. The mace rises smoothly, spins and moves toward the opponent. Left-click its opaque model pixels to catch it; the owner and other players can catch immediately. There is no pickup cooldown. Catch detection accounts for the interpolated poses, retains the narrow sprite geometry, respects walls and never uses the display's large render bounds as a hitbox. Full inventories leave the payload intact. Unload, reload and expiry recover the real item with no residual pickup delay.
- **Glitch** (`glitch`): Wind Burst I, Density IV. Each attack independently rolls one outcome: 1% to a safe point near the actual world border, the next 5% to a safe surface 15–20 blocks away, otherwise no teleport. The wielder is teleported. Hazardous or unsupported destinations are rejected, and a successful teleport grants brief fall-damage protection.
- `/lwv give <player> tv_safe` and `/lwv give <player> glitch_safe` issue curse-free variants. Both maces can be enchanted normally; Density is capped at IV and Breach at III when enchanting, using an anvil or refreshing existing items.
- Configuration: `items/TelevisionMace.yml` and `items/GlitchMace.yml`. Models retain the resource pack's CMD thresholds 145 and 138, respectively. Names, descriptions and curse-free text support RU/EN.

`tests/MaceAudit.java` exercises instant owner/other-player catches, payload identity and enchantment preservation, full inventories, duplicate prevention, release recovery, rapid-click curse rolls and actual safe teleport/fall protection on a disposable server.

## Midas Sword, Emerald Blade and Excalibur

- **Midas Sword** (`midas`) starts at Sharpness V and gains one level for each new enemy player killed, up to XXVI. Victim UUIDs belong to that sword and persist across transfers and restarts; repeated kills of the same player do not raise Sharpness. Mob kills drop 8 gold nuggets; player kills drop 27 nuggets and 6 ingots. Scoreboard allies with friendly fire disabled do not grant Sharpness. Configure limits and rewards in `items/MidasSword.yml`.
- **Emerald Blade** (`emerald`) opens its own two-row storage with Shift + right-click. Each 64 emerald blocks grants one Sharpness level, up to XVIII. Deposits can be withdrawn, and closing the menu recalculates the sword from the remaining blocks. Storage preserves item metadata and stays with the physical sword. The menu locks that sword against hotbar, off-hand and drop transfers while open; foreign materials are rejected. Configure currency, capacity and progression in `items/EmeraldBlade.yml`.
- **Excalibur** (`excalibur`) activates on right-click and blocks 6 hits or lasts 25 seconds. Hits within 10 ticks share a guard; damage below 1.5 is blocked without consuming a guard. The 52-second recharge begins when protection ends, including early exhaustion or logout. A reservation prevents relogging from resetting the ability. Native and pure damage use the same protection, before gauntlet totems. Configure this in `items/Excalibur.yml`.
- Midas and Emerald keep their Sharpness enchantment visible and update the displayed attack damage after progression. All three retain their original resource-pack model paths and have RU/EN names and descriptions.

Gameplay modules are separated by weapon; menu handling, bounded item storage, cooldown feedback and server compatibility have distinct responsibilities. No framework, pedestal, ban API or Kotlin runtime is required. `tests/SwordAudit.java` covers unique kills, gold, persistent storage, menu transfer guards, visible Sharpness, pure-damage protection and recharge on hit exhaustion/timed expiry.


### Combat fixes

Fully charged gauntlet and Lucky Sword attacks use attack strength captured before the server resets it. Scythe life theft bypasses held totems on lethal damage, including the Lucky Sword outcome; other attacks keep vanilla resurrection. Blocked life theft does not consume the Lucky Sword power. Creative/spectator targets are excluded from the gauntlet bash and pure-damage execution. Non-finite numeric gauntlet settings fall back to defaults before reaching health or velocity packets.

`tests/CombatRegressionAudit.java` covers pre-reset melee strength, native lethal totem handling, cancelled theft, retry behavior and creative/spectator immunity.


### Native combat regression checks

Stored gauntlet totems accept the native resurrection callback at zero health. The Lucky Sword frost outcome follows the original FrostSword rules: every landed hit may add 55 ticks of remaining cold with a 10-tick throttle; cold decays between hits, progresses through the 40/230/350 thresholds, and ends in a 100-tick stun with ice displays. Frost no longer requires a fully charged melee attack. These values are configurable under `frost`.

`tests/NativeCombatAudit.java` creates server-side native players and exercises real melee, shield blocking and the zero-health resurrection path in addition to the isolated mechanics tests. It checks partial-strength frost hits, stun movement and teleport restrictions, cleanup and post-totem protection. Test helpers are never bundled in the plugin.
