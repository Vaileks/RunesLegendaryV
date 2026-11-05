# RunesLegendaryV

Paper 1.21.11 · Java 21+ · Vaileks

Самостоятельный плагин: меч жизни, судный меч, рельсатрон и перчатка стойкости. Зависимостей от других плагинов нет. Предметы используют модели и звуки существующего серверного ресурспака; файлы ресурспака в плагин не включены.

## Команды

- `/rlv` — меню выдачи.
- `/rlv give <игрок> <life|judge|rail|glove> [количество]` — выдать предмет.
- `/rlv reload` — перечитать настройки.
- `/rlv resetcd <игрок>` — сбросить перезарядки.

Право: `runeslegendaryv.admin`, по умолчанию у операторов.

## Настройки

`plugins/RunesLegendaryV/config.yml` — общие настройки.

`plugins/RunesLegendaryV/items/` — четыре отдельных файла предметов. Здесь настраиваются способности, урон, перезарядки, модели, звуки и описание. Рецептов и пьедесталов нет. Судный меч не выдаёт баны.

## Сборка

Установить JDK 21+ и Python 3. Paper API 1.21.11 и зависимости Adventure, JOML, Guava, JetBrains/JSPECIFY и BungeeCord Chat должны находиться в локальном Maven-кэше `~/.m2/repository`. Можно указать путь к Paper API через `PAPER_API`, к JDK через `JAVA_HOME`.

`python3 build.py`

Результат: `RunesLegendaryV.jar`.

`tests/StandaloneSmoke.java` — проверка запуска, предметов, лозы, кулдаунов, сериализации тотемов и чистого урона на отдельном тестовом Paper-сервере. Тестовый помощник завершает сервер после проверки; его нельзя устанавливать на рабочий сервер.

## Language

Set `language: en` or `language: ru` in `plugins/RunesLegendaryV/config.yml`, then run `/rlv reload`.

Edit messages, menu titles, item names, descriptions and charge indicators in `lang/en.yml` and `lang/ru.yml`. Configure gameplay in `items/`. Missing translations use the bundled text for the selected language. Legacy default Russian text migrates automatically. Custom text in an item configuration remains an explicit override; remove that text field to use the language bundle.

Existing items update on login, inventory opening, pickup and reload without resetting their models, attributes, stored totems or cooldowns.

Commands: `/rlv` opens the admin item menu; `/rlv give <player> <life|judge|rail|glove> [amount]` gives an item; `/rlv resetcd <player>` resets cooldowns. Permission: `runeslegendaryv.admin` (operators by default). Requires Paper 1.21.11 and Java 21+. No other plugins are required. Custom models and sounds require the server resource pack, which is not bundled in this JAR.

`tests/LocalizationAudit.java` checks Russian/English key parity and placeholders, English tooltips/action bars, reload, legacy migration, custom overrides, and preservation of item data.
