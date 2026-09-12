package dev.infobar;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InfobarPlugin extends JavaPlugin implements Listener {

    private static final class Bar {
        String id;
        boolean enabled;
        String text;
        List<String> frames;
        BossBar.Color color;
        BossBar.Overlay overlay;
        String progressSpec;
        int intervalSeconds;
        int frameIndex = 0;
        int ticksSinceUpdate = 0;
    }

    private final Map<String, Bar> bars = new LinkedHashMap<>();
    // bar id -> (player uuid -> that player's boss bar instance)
    private final Map<String, Map<UUID, BossBar>> active = new ConcurrentHashMap<>();
    private final Set<UUID> optedOut = ConcurrentHashMap.newKeySet();
    private File optOutFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadBars();
        optOutFile = new File(getDataFolder(), "optout.yml");
        loadOptOuts();
        getServer().getPluginManager().registerEvents(this, this);
        for (Player player : getServer().getOnlinePlayers()) showAllBars(player);
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("InfoBar " + getDescription().getVersion() + " enabled (" + bars.size() + " bars).");
    }

    @Override
    public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) hideAllBars(player);
    }

    /**
     * Bukkit reads '.' as a path separator: a bar named "my.bar" silently became a
     * nested section and showed up as an EMPTY bar called "my" (found 12.09.2026).
     * A separator that cannot occur in a name switches that off. It must be set
     * BEFORE loading - loadConfiguration()/getConfig() already build the tree.
     */
    private static final char SEP = '\u0001';

    private void loadBars() {
        bars.clear();
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().pathSeparator(SEP);
        try {
            yml.load(new File(getDataFolder(), "config.yml"));
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            getLogger().warning("Could not read config.yml, no bars loaded: " + e.getMessage());
            return;
        }
        ConfigurationSection root = yml.getConfigurationSection("bars");
        if (root == null) return;
        for (Map.Entry<String, Object> entry : root.getValues(false).entrySet()) {
            String id = entry.getKey();
            if (!(entry.getValue() instanceof ConfigurationSection s)) {
                getLogger().warning("Bar '" + id + "' is not a section (expected text/color/... below it) - skipped.");
                continue;
            }
            Bar bar = new Bar();
            bar.id = id;
            bar.enabled = s.getBoolean("enabled", true);
            bar.text = s.getString("text", "");
            // 'frames: single text' is a common YAML slip; treat it as a one-entry list
            // instead of silently falling back to 'text' (12.09.2026).
            bar.frames = s.isString("frames") ? List.of(s.getString("frames")) : s.getStringList("frames");
            try {
                bar.color = BossBar.Color.valueOf(s.getString("color", "WHITE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bar '" + id + "': unknown color, falling back to WHITE.");
                bar.color = BossBar.Color.WHITE;
            }
            try {
                bar.overlay = BossBar.Overlay.valueOf(s.getString("overlay", "PROGRESS").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bar '" + id + "': unknown overlay, falling back to PROGRESS.");
                bar.overlay = BossBar.Overlay.PROGRESS;
            }
            bar.progressSpec = String.valueOf(s.get("progress", 1.0));
            bar.intervalSeconds = Math.max(1, s.getInt("update-interval-seconds", 5));
            bars.put(id, bar);
        }
    }

    private void loadOptOuts() {
        optedOut.clear();
        if (!optOutFile.exists()) return;
        for (String key : YamlConfiguration.loadConfiguration(optOutFile).getStringList("opted-out")) {
            try {
                optedOut.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveOptOuts() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("opted-out", optedOut.stream().map(UUID::toString).toList());
        try {
            yml.save(optOutFile);
        } catch (IOException e) {
            getLogger().warning("Could not save optout.yml: " + e.getMessage());
        }
    }

    private void tick() {
        for (Bar bar : bars.values()) {
            if (!bar.enabled) continue;
            bar.ticksSinceUpdate++;
            if (bar.ticksSinceUpdate < bar.intervalSeconds) continue;
            bar.ticksSinceUpdate = 0;
            if (!bar.frames.isEmpty()) bar.frameIndex = (bar.frameIndex + 1) % bar.frames.size();

            Map<UUID, BossBar> viewers = active.get(bar.id);
            if (viewers == null || viewers.isEmpty()) continue;
            String template = currentText(bar);
            for (Map.Entry<UUID, BossBar> entry : viewers.entrySet()) {
                Player player = getServer().getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) continue;
                BossBar bossBar = entry.getValue();
                bossBar.name(Msg.parse(substitute(template, player)));
                bossBar.progress(resolveProgress(bar, player.getWorld()));
            }
        }
    }

    private String currentText(Bar bar) {
        return bar.frames.isEmpty() ? bar.text : bar.frames.get(bar.frameIndex);
    }

    private float resolveProgress(Bar bar, World world) {
        if ("time".equalsIgnoreCase(bar.progressSpec)) {
            long ticks = world.getTime() % 24000;
            return (float) (ticks / 24000.0);
        }
        try {
            return Math.max(0f, Math.min(1f, Float.parseFloat(bar.progressSpec)));
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    private String substitute(String template, Player player) {
        double[] tps = Bukkit.getTPS();
        return template
                .replace("{player}", player.getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{tps}", String.format(Locale.ROOT, "%.1f", tps.length > 0 ? tps[0] : 20.0))
                .replace("{world}", player.getWorld().getName())
                .replace("{time}", clockString(player.getWorld()));
    }

    /** Minecraft's day starts at tick 0 = 06:00. */
    private String clockString(World world) {
        long shifted = (world.getTime() % 24000 + 6000) % 24000;
        long hours = shifted / 1000;
        long minutes = (shifted % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private void showAllBars(Player player) {
        for (Bar bar : bars.values()) showBar(player, bar);
    }

    private void showBar(Player player, Bar bar) {
        if (!bar.enabled) return;
        if (!player.hasPermission("infobar.see")) return;
        if (optedOut.contains(player.getUniqueId())) return;
        float progress = resolveProgress(bar, player.getWorld());
        Component name = Msg.parse(substitute(currentText(bar), player));
        BossBar bossBar = BossBar.bossBar(name, progress, bar.color, bar.overlay);
        player.showBossBar(bossBar);
        active.computeIfAbsent(bar.id, k -> new ConcurrentHashMap<>()).put(player.getUniqueId(), bossBar);
    }

    private void hideAllBars(Player player) {
        UUID id = player.getUniqueId();
        for (Map<UUID, BossBar> viewers : active.values()) {
            BossBar bossBar = viewers.remove(id);
            if (bossBar != null) player.hideBossBar(bossBar);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        showAllBars(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (Map<UUID, BossBar> viewers : active.values()) viewers.remove(id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("infobar.admin")) {
                sender.sendMessage(Component.text("[InfoBar] No permission."));
                return true;
            }
            for (Player player : getServer().getOnlinePlayers()) hideAllBars(player);
            active.clear();
            loadBars();
            for (Player player : getServer().getOnlinePlayers()) showAllBars(player);
            sender.sendMessage(Component.text("[InfoBar] Config reloaded (" + bars.size() + " bars)."));
            return true;
        }
        if (sender instanceof Player player) {
            if (optedOut.remove(player.getUniqueId())) {
                showAllBars(player);
                sender.sendMessage(Component.text("[InfoBar] Boss bars enabled."));
            } else {
                optedOut.add(player.getUniqueId());
                hideAllBars(player);
                sender.sendMessage(Component.text("[InfoBar] Boss bars disabled."));
            }
            saveOptOuts();
        } else {
            sender.sendMessage(Component.text("[InfoBar] /infobar reload — or run /infobar as a player to toggle."));
        }
        return true;
    }
}
