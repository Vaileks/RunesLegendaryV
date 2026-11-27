package dev.vaileks.runeslegendaryv;

import dev.vaileks.runeslegendaryv.compat.ServerCompat;
import dev.vaileks.runeslegendaryv.config.Language;
import dev.vaileks.runeslegendaryv.config.Settings;
import dev.vaileks.runeslegendaryv.listener.CombatListener;
import dev.vaileks.runeslegendaryv.listener.EquipmentListener;
import dev.vaileks.runeslegendaryv.listener.PlayerSessionListener;
import dev.vaileks.runeslegendaryv.listener.TotemMenuListener;
import dev.vaileks.runeslegendaryv.support.DamageBridge;
import dev.vaileks.runeslegendaryv.support.DamageGuard;
import dev.vaileks.runeslegendaryv.support.DeferredCooldowns;
import dev.vaileks.runeslegendaryv.support.Feedback;
import dev.vaileks.runeslegendaryv.util.AbilityCooldown;
import dev.vaileks.runeslegendaryv.util.Cooldowns;
import dev.vaileks.runeslegendaryv.util.Debug;
import dev.vaileks.runeslegendaryv.util.Keys;
import dev.vaileks.runeslegendaryv.util.Tasks;
import dev.vaileks.runeslegendaryv.weapon.AbstractLegendaryWeapon;
import dev.vaileks.runeslegendaryv.weapon.DragonKatana;
import dev.vaileks.runeslegendaryv.weapon.Excalibur;
import dev.vaileks.runeslegendaryv.weapon.MidasSword;
import dev.vaileks.runeslegendaryv.weapon.ReaperScythe;
import dev.vaileks.runeslegendaryv.weapon.ULegendaryContext;
import dev.vaileks.runeslegendaryv.weapon.artemis.ArtemisBow;
import dev.vaileks.runeslegendaryv.weapon.emerald.EmeraldBlade;
import dev.vaileks.runeslegendaryv.weapon.emerald.EmeraldMenu;
import dev.vaileks.runeslegendaryv.weapon.epic.FlamingCrossbow;
import dev.vaileks.runeslegendaryv.weapon.epic.NetheriteShield;
import dev.vaileks.runeslegendaryv.weapon.gamble.GambleSword;
import dev.vaileks.runeslegendaryv.weapon.judge.JudgeSword;
import dev.vaileks.runeslegendaryv.weapon.maces.MaceCurses;
import dev.vaileks.runeslegendaryv.weapon.maces.MaceWeapon;
import dev.vaileks.runeslegendaryv.weapon.mechzhizni.SwordOfNature;
import dev.vaileks.runeslegendaryv.weapon.perchatkastoikosti.Gauntlet;
import dev.vaileks.runeslegendaryv.weapon.relsatron.RailGun;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RunesLegendaryV extends JavaPlugin implements Listener, TabExecutor {
    private static RunesLegendaryV instance;
    private Language language;
    private Settings settings;
    private ULegendaryContext context;
    private SwordOfNature nature;
    private Gauntlet glove;
    private RailGun rail;
    private JudgeSword judge;
    private GambleSword gamble;
    private FlamingCrossbow flaming;
    private NetheriteShield shield;
    private DragonKatana katana;
    private MaceWeapon television, glitch;
    private MaceCurses maces;
    private MidasSword midas;
    private EmeraldBlade emerald;
    private EmeraldMenu emeraldMenu;
    private Excalibur excalibur;

    public MidasSword midasSword() {
        return midas;
    }

    public EmeraldBlade emeraldBlade() {
        return emerald;
    }

    public Excalibur excalibur() {
        return excalibur;
    }

    public MaceWeapon televisionMace() {
        return television;
    }

    public MaceWeapon glitchMace() {
        return glitch;
    }

    public MaceCurses maceCurses() {
        return maces;
    }

    private ReaperScythe reaper;
    private ArtemisBow artemis;

    public ReaperScythe reaperScythe() {
        return reaper;
    }

    public ArtemisBow artemisBow() {
        return artemis;
    }

    private final Map<String, YamlConfiguration> configs = new LinkedHashMap<>();
    private final Map<String, AbstractLegendaryWeapon> weapons = new LinkedHashMap<>();

    public static RunesLegendaryV instance() {
        return instance;
    }

    public Settings settings() {
        return settings;
    }

    public SwordOfNature swordOfNature() {
        return nature;
    }

    public Gauntlet gauntlet() {
        return glove;
    }

    public RailGun railGun() {
        return rail;
    }

    public JudgeSword judgeSword() {
        return judge;
    }

    public GambleSword gambleSword() {
        return gamble;
    }

    public FlamingCrossbow flamingCrossbow() {
        return flaming;
    }

    public FileConfiguration itemConfig(String key) {
        return configs.get(key);
    }

    public Map<String, AbstractLegendaryWeapon> weapons() {
        return Collections.unmodifiableMap(weapons);
    }

    @Override
    public void onEnable() {
        instance = this;
        migrateDataFolder();
        saveDefaultConfig();
        initialize();
        getCommand("legendaryweaponsvl").setExecutor(this);
        getCommand("legendaryweaponsvl").setTabCompleter(this);
        getLogger()
                .info(
                        "Eleven legendary and four epic items loaded. No external plugin"
                                + " dependencies.");
    }

    private static boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("legendaryweaponsvl.admin")
                || sender.hasPermission("runeslegendaryv.admin");
    }

    private void migrateDataFolder() {
        java.nio.file.Path current = getDataFolder().toPath();
        java.nio.file.Path previous = current.resolveSibling("RunesLegendaryV");
        if (java.nio.file.Files.exists(current)
                || !java.nio.file.Files.isDirectory(
                        previous, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try {
            java.nio.file.Files.move(previous, current);
            getLogger().info("Existing configuration moved to LegendaryWeaponsVL.");
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Cannot move existing configuration to " + current, error);
        }
    }

    private void initialize() {
        ServerCompat.start(this);
        configs.clear();
        weapons.clear();
        reloadConfig();
        language = new Language(this);
        var combined = new YamlConfiguration();
        for (String key : getConfig().getKeys(false)) combined.set(key, getConfig().get(key));
        for (String key :
                List.of(
                        "mechzhizni",
                        "JudgeSword",
                        "relsatron",
                        "perchatkastoikosti",
                        "GambleSword",
                        "FlamingCrossbow",
                        "NetheriteShield",
                        "DragonKatana",
                        "ReaperScythe",
                        "ArtemisBow",
                        "TelevisionMace",
                        "GlitchMace",
                        "MidasSword",
                        "EmeraldBlade",
                        "Excalibur")) {
            String path = "items/" + key + ".yml";
            File file = new File(getDataFolder(), path);
            if (!file.exists()) saveResource(path, false);
            var yaml = new YamlConfiguration();
            try {
                yaml.load(file);
            } catch (Exception error) {
                throw new IllegalStateException("Cannot load " + file, error);
            }
            try (var resource = getResource(path)) {
                if (resource == null) throw new IOException("Missing bundled defaults: " + path);
                yaml.setDefaults(
                        YamlConfiguration.loadConfiguration(
                                new InputStreamReader(resource, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            language.applyItem(key, yaml, file);
            configs.put(key, yaml);
            combined.set(key, yaml);
        }
        settings = new Settings(combined);
        Debug.bind(getLogger(), getConfig().getBoolean("debug"));
        context =
                new ULegendaryContext(
                        this,
                        settings,
                        new Keys(this),
                        new Cooldowns(),
                        new AbilityCooldown(),
                        new Tasks(this));
        nature = new SwordOfNature(context);
        glove = new Gauntlet(context);
        rail = new RailGun(context);
        judge = new JudgeSword(context);
        gamble = new GambleSword(context);
        flaming = new FlamingCrossbow(context);
        shield = new NetheriteShield(context);
        katana = new DragonKatana(context);
        reaper = new ReaperScythe(context);
        artemis = new ArtemisBow(context);
        television = new MaceWeapon(context, "TelevisionMace");
        glitch = new MaceWeapon(context, "GlitchMace");
        maces = new MaceCurses(this);
        midas = new MidasSword(context);
        emerald = new EmeraldBlade(context);
        emeraldMenu = new EmeraldMenu(this, emerald);
        excalibur = new Excalibur(context);
        for (var weapon :
                List.of(
                        nature,
                        judge,
                        rail,
                        glove,
                        gamble,
                        flaming,
                        shield,
                        katana,
                        reaper,
                        artemis,
                        television,
                        glitch,
                        midas,
                        emerald,
                        excalibur)) weapons.put(weapon.getName(), weapon);
        for (Listener listener :
                List.of(
                        this,
                        new CombatListener(this, context),
                        new EquipmentListener(this, context),
                        new PlayerSessionListener(this, context),
                        new TotemMenuListener(glove),
                        new DamageGuard(),
                        gamble,
                        flaming,
                        katana,
                        reaper,
                        artemis,
                        midas,
                        emeraldMenu,
                        excalibur)) getServer().getPluginManager().registerEvents(listener, this);
        for (Listener listener : judge.listeners())
            getServer().getPluginManager().registerEvents(listener, this);
        ServerCompat.registerCombat(this);
        excalibur.start();
        maces.start();
        artemis.start();
        gamble.start();
        nature.startPassiveTasks();
        rail.startFlightTask();
        judge.start();
        context.tasks().globalTimer(2, 2, glove::tick);
        for (Player player : Bukkit.getOnlinePlayers()) {
            localizeInventory(player.getInventory());
            glove.syncStats(player);
        }
    }

    private void stop() {
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder()
                            instanceof dev.vaileks.runeslegendaryv.menu.TotemMenu
                    || player.getOpenInventory().getTopInventory().getHolder() instanceof RelicMenu)
                player.closeInventory();
        if (emeraldMenu != null) emeraldMenu.clear();
        if (excalibur != null) excalibur.clear();
        if (maces != null) maces.clear();
        if (reaper != null) reaper.clear();
        if (artemis != null) artemis.clear();
        if (gamble != null) gamble.clearAll();
        if (nature != null) nature.clear();
        if (rail != null) rail.clearAll();
        if (glove != null) glove.clearAll();
        if (judge != null) judge.clear();
        Feedback.clearAll();
        DeferredCooldowns.clear();
        DamageBridge.clear();
        Bukkit.getScheduler().cancelTasks(this);
    }

    @Override
    public void onDisable() {
        stop();
    }

    public Language language() {
        return language;
    }

    public void localizeInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;
            for (var weapon : weapons.values())
                if (weapon.isItem(item)) {
                    weapon.refreshText(item);
                    inventory.setItem(slot, item);
                    break;
                }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventoryOpened(org.bukkit.event.inventory.InventoryOpenEvent event) {
        localizeInventory(event.getInventory());
        localizeInventory(event.getPlayer().getInventory());
    }

    private static String canonical(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "life", "мечжизни" -> "mechzhizni";
            case "judge", "judgesword", "судныймеч" -> "JudgeSword";
            case "rail", "рельсатрон" -> "relsatron";
            case "ludo", "gamble", "лудо-меч" -> "GambleSword";
            case "flaming", "пламенный" -> "FlamingCrossbow";
            case "katana", "dragonkatana", "катана" -> "DragonKatana";
            case "reaper", "reaperscythe", "коса", "жнец" -> "ReaperScythe";
            case "artemis", "artemisbow", "артемида" -> "ArtemisBow";
            case "tv", "television", "tv_safe", "television_safe", "телевизор" -> "TelevisionMace";
            case "glitch", "glitch_safe", "glitched", "глитч", "глюк" -> "GlitchMace";
            case "midas", "midassword", "мидас", "мидаска" -> "MidasSword";
            case "emerald", "emeraldblade", "изумрудка" -> "EmeraldBlade";
            case "excalibur", "экскалибур", "эскалибур" -> "Excalibur";
            case "shield", "щит" -> "NetheriteShield";
            case "glove", "перчатка" -> "perchatkastoikosti";
            default -> value;
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAdmin(sender)) {
            ServerCompat.sendMessage(sender, Language.component("messages.no-permission"));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) p.openInventory(new RelicMenu().getInventory());
            else
                ServerCompat.sendMessage(
                        sender, Language.component("messages.console-usage", "command", label));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            stop();
            HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
            initialize();
            ServerCompat.sendMessage(sender, Language.component("messages.reloaded"));
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            var weapon = weapons.get(canonical(args[2]));
            if (target == null || weapon == null) {
                ServerCompat.sendMessage(sender, Language.component("messages.not-found"));
                return true;
            }
            int amount = 1;
            try {
                if (args.length > 3) amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                ServerCompat.sendMessage(sender, Language.component("messages.invalid-amount"));
                return true;
            }
            if (amount < 1 || amount > 64) {
                ServerCompat.sendMessage(sender, Language.component("messages.invalid-amount"));
                return true;
            }
            for (int i = 0; i < amount; i++)
                give(target, weapon, args[2].toLowerCase(Locale.ROOT).endsWith("_safe"));
            ServerCompat.sendMessage(
                    sender, Language.component("messages.given", "player", target.getName()));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetcd")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                ServerCompat.sendMessage(sender, Language.component("messages.player-not-found"));
                return true;
            }
            resetCooldowns(target);
            ServerCompat.sendMessage(sender, Language.component("messages.reset"));
            return true;
        }
        ServerCompat.sendMessage(sender, Language.component("messages.usage", "command", label));
        return true;
    }

    public void resetCooldowns(Player target) {
        context.cooldowns().clearAll(target);
        context.abilityCooldown().clearAll(target);
        Feedback.clear(target);
        for (var key : new HashSet<>(target.getPersistentDataContainer().getKeys()))
            if (cooldownKey(key)) target.getPersistentDataContainer().remove(key);
        var inventory = target.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || weapons.values().stream().noneMatch(w -> w.isItem(item))) continue;
            var meta = item.getItemMeta();
            for (var key : new HashSet<>(meta.getPersistentDataContainer().getKeys()))
                if (cooldownKey(key)) meta.getPersistentDataContainer().remove(key);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            ServerCompat.setCooldown(target, item, 0);
        }
    }

    private static boolean cooldownKey(NamespacedKey key) {
        return key.getNamespace().equals("runeslegendaryv")
                && (key.getKey().startsWith("cd_") || key.getKey().equals("gauntlet_bash_until"));
    }

    private void give(Player player, AbstractLegendaryWeapon weapon) {
        give(player, weapon, false);
    }

    private void give(Player player, AbstractLegendaryWeapon weapon, boolean safe) {
        ItemStack given = weapon.getProcessedItem();
        if (safe && weapon instanceof MaceWeapon mace) mace.setCursed(given, false);
        player.getInventory()
                .addItem(given)
                .values()
                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        glove.syncStats(player);
    }

    private final class RelicMenu implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, AbstractLegendaryWeapon> slots = new LinkedHashMap<>();

        RelicMenu() {
            inventory =
                    ServerCompat.createInventory(
                            this,
                            ((weapons.size() + 6) / 7 + 2) * 9,
                            Language.component("menu.title"));
            int index = 0;
            for (var weapon : weapons.values()) {
                int row = index / 7;
                int count = Math.min(7, weapons.size() - row * 7);
                int slot = (row + 1) * 9 + (9 - count) / 2 + index % 7;
                slots.put(slot, weapon);
                inventory.setItem(slot, weapon.getProcessedItem());
                index++;
            }
        }

        public Inventory getInventory() {
            return inventory;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void menu(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RelicMenu menu)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p && isAdmin(p)) {
            var weapon = menu.slots.get(event.getRawSlot());
            if (weapon != null) give(p, weapon);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RelicMenu)
            event.setCancelled(true);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!isAdmin(sender)) return List.of();
        List<String> values =
                args.length == 1
                        ? List.of("menu", "give", "reload", "resetcd")
                        : args.length == 2
                                        && (args[0].equalsIgnoreCase("give")
                                                || args[0].equalsIgnoreCase("resetcd"))
                                ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                                : args.length == 3 && args[0].equalsIgnoreCase("give")
                                        ? new ArrayList<>(weapons.keySet())
                                        : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
