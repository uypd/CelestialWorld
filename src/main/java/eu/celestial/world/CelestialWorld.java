package eu.celestial.world;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CelestialWorld extends JavaPlugin implements Listener {

    // Eigene Pack-ID, damit andere Packs (z.B. CelestialRanks) nicht ersetzt werden
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("celestialworld-halloween".getBytes(StandardCharsets.UTF_8));

    private HttpServer server;
    private ExecutorService executor;
    private byte[] packBytes;
    private String packHash;
    private String packUrl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!loadPack()) {
            getLogger().severe("pack.zip nicht in der JAR gefunden - Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupPackUrl();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Celestial World aktiv. Pack-URL: " + packUrl + " (SHA-1 " + packHash + ")");
    }

    @Override
    public void onDisable() {
        stopWebserver();
    }

    private boolean loadPack() {
        try (InputStream in = getResource("pack.zip")) {
            if (in == null) return false;
            packBytes = in.readAllBytes();
            packHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(packBytes));
            return true;
        } catch (Exception e) {
            getLogger().severe("Pack konnte nicht geladen werden: " + e.getMessage());
            return false;
        }
    }

    // Standard: Pack kommt von GitHub. Ist resourcepack.url leer, startet der eingebaute Webserver.
    private void setupPackUrl() {
        stopWebserver();
        String url = getConfig().getString("resourcepack.url", "").trim();
        if (!url.isEmpty()) {
            packUrl = url;
            return;
        }
        startWebserver();
    }

    private void startWebserver() {
        int port = getConfig().getInt("webserver.port", 8123);
        String host = getConfig().getString("webserver.host", "localhost");
        packUrl = "http://" + host + ":" + port + "/pack.zip";
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/pack.zip", ex -> {
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, packBytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(packBytes);
                }
            });
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            server.start();
        } catch (IOException e) {
            getLogger().severe("Webserver konnte nicht auf Port " + port + " starten: " + e.getMessage());
        }
    }

    private void stopWebserver() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void sendPack(Player p) {
        boolean force = getConfig().getBoolean("resourcepack.force", true);
        Component prompt = LegacyComponentSerializer.legacySection()
                .deserialize(getConfig().getString("resourcepack.prompt", ""));
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(packUrl), packHash);
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(force)
                .replace(false)
                .prompt(prompt)
                .build());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (getConfig().getBoolean("resourcepack.send-on-join", true)) {
            Player p = e.getPlayer();
            // kurz warten, bis der Spieler vollstaendig geladen ist
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline()) sendPack(p);
            }, 20L);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("celestialworld.admin")) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§6/cw reload §7- Config neu laden\n§6/cw pack §7- Pack an alle Spieler senden");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                setupPackUrl();
                sender.sendMessage("§aCelestial World neu geladen. §7" + packUrl);
            }
            case "pack" -> {
                Bukkit.getOnlinePlayers().forEach(this::sendPack);
                sender.sendMessage("§aPack an alle Spieler gesendet.");
            }
            default -> sender.sendMessage("§cUnbekannter Befehl. §7/cw reload | /cw pack");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return a.length == 1 ? List.of("reload", "pack") : List.of();
    }
}
