# RunesLegendaryV

Standalone Paper 1.21.11 plugin by Vaileks. Includes Sword of Life, Judge Sword, Railgun and Gauntlet of Fortitude. Java 21+ is required; other plugins are not required.

Custom models, animated vines, tooltip styles and sounds require the compatible server resource pack, distributed through the Discord link below. The resource pack is not bundled in the JAR.

## Commands

- `/rlv` — open the admin item menu.
- `/rlv give <player> <life|judge|rail|glove> [amount]` — give an item.
- `/rlv reload` — reload settings and language.
- `/rlv resetcd <player>` — reset cooldowns.

Full command: `/runeslegendaryv`. Permission: `runeslegendaryv.admin` (operators by default).

## Configuration

- `plugins/RunesLegendaryV/config.yml` — general settings and `language: ru` or `language: en`.
- `plugins/RunesLegendaryV/items/` — damage, cooldowns, abilities, models and sounds for each item.
- `plugins/RunesLegendaryV/lang/` — messages, menus, names and descriptions.

Apply changes with `/rlv reload`. Custom text in an item configuration overrides the selected translation; remove that field to use the language file. Legacy default Russian text migrates automatically. Updating item text preserves models, attributes, stored totems and cooldowns.

There are no pedestal recipes or ban integrations.

## Build

Requires Python 3, JDK 21+ and Paper API 1.21.11 with Adventure, JOML, Guava, JetBrains/JSPECIFY and BungeeCord Chat dependencies in the local Maven cache (`~/.m2/repository`). Optional environment variables: `JAVA_HOME` and `PAPER_API`.

```sh
python3 build.py
```

Output: `RunesLegendaryV.jar`.

`tests/StandaloneSmoke.java` and `tests/LocalizationAudit.java` check item mechanics, cooldowns, totem serialization and RU/EN localization on a disposable Paper test server. The test helper shuts down the server and must not be installed on a production server.

## Links

- [Modrinth](https://modrinth.com/plugin/runeslegendaryv)
- [Resource pack and support](https://discord.gg/ssXctkBkQD)
- [Source](https://github.com/Vaileks/RunesLegendaryV)

Vine resource-pack additions: run `python3 tools/export_plant_vines.py <model.bbmodel> SwordOfLife-RP.zip` using the supplied eight-section model. Merge the archive’s `assets/` folder into the compatible pack and reload the pack on clients. The growth/wrapping timing and movement are unchanged; the plugin selects the eight matching meshes. The model and texture are not embedded in the JAR.
