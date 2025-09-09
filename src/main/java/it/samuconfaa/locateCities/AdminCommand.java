package it.samuconfaa.locateCities;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class AdminCommand implements CommandExecutor {

    private final LocateCities plugin;
    private final CityManager cityManager;
    private final StatisticsManager statisticsManager;

    public AdminCommand(LocateCities plugin, CityManager cityManager, StatisticsManager statisticsManager) {
        this.plugin = plugin;
        this.cityManager = cityManager;
        this.statisticsManager = statisticsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("locatecities.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;

            case "clearcache":
                handleClearCache(sender);
                break;

            case "info":
                handleInfo(sender);
                break;

            case "setorigin":
                handleSetOrigin(sender, args);
                break;

            case "setscale":
                handleSetScale(sender, args);
                break;

            case "stats":
                handleStats(sender);
                break;

            case "near":
                handleNear(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== LocateCities Admin ===");
        sender.sendMessage(ChatColor.YELLOW + "/cittaadmin reload" + ChatColor.GRAY + " - Ricarica la configurazione");
        sender.sendMessage(ChatColor.YELLOW + "/cittaadmin clearcache" + ChatColor.GRAY + " - Pulisce la cache");
        sender.sendMessage(ChatColor.YELLOW + "/cittaadmin info" + ChatColor.GRAY + " - Mostra informazioni plugin");
        sender.sendMessage(ChatColor.YELLOW + "/cittaadmin setorigin <lat> <lon>" + ChatColor.GRAY + " - Imposta origine mappa");
        sender.sendMessage(ChatColor.YELLOW + "/cittaadmin setscale <scala>" + ChatColor.GRAY + " - Imposta scala mappa");
        sender.sendMessage(ChatColor.YELLOW + "/cittaadmin stats" + ChatColor.GRAY + " - Mostra statistiche dettagliate");
        sender.sendMessage(ChatColor.YELLOW + "/cittaadmin near <città>" + ChatColor.GRAY + " - Mostra città simili cercate");
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        sender.sendMessage(plugin.getConfigManager().getMessage("config_reloaded"));
    }

    private void handleClearCache(CommandSender sender) {
        cityManager.clearExpiredCache();
        sender.sendMessage(plugin.getConfigManager().getMessage("cache_cleared"));
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== LocateCities Info ===");
        sender.sendMessage(ChatColor.YELLOW + "Versione: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Origine mappa: " + ChatColor.WHITE +
                String.format("%.4f, %.4f",
                        plugin.getConfigManager().getLatOrigin(),
                        plugin.getConfigManager().getLonOrigin()));
        sender.sendMessage(ChatColor.YELLOW + "Scala: " + ChatColor.WHITE + plugin.getConfigManager().getScale());
        sender.sendMessage(ChatColor.YELLOW + "Teleport: " + ChatColor.WHITE +
                (plugin.getConfigManager().isTeleportEnabled() ? "Abilitato" : "Disabilitato"));
        sender.sendMessage(ChatColor.YELLOW + "Economy: " + ChatColor.WHITE +
                (plugin.getConfigManager().isEconomyEnabled() ? "Abilitata" : "Disabilitata"));
        sender.sendMessage(ChatColor.YELLOW + "Rate Limiting: " + ChatColor.WHITE +
                (plugin.getConfigManager().isRateLimitEnabled() ? "Abilitato" : "Disabilitato"));

        // Statistiche rapide
        sender.sendMessage(ChatColor.GOLD + "=== Statistiche Rapide ===");
        sender.sendMessage(ChatColor.YELLOW + "Ricerche totali: " + ChatColor.WHITE + statisticsManager.getTotalSearches());
        sender.sendMessage(ChatColor.YELLOW + "Teleport totali: " + ChatColor.WHITE + statisticsManager.getTotalTeleports());
        sender.sendMessage(ChatColor.YELLOW + "Cache hit rate: " + ChatColor.WHITE +
                String.format("%.1f%%", statisticsManager.getCacheHitRate()));
    }

    private void handleSetOrigin(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin setorigin <latitudine> <longitudine>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin setorigin 41.9028 12.4964");
            return;
        }

        try {
            double lat = Double.parseDouble(args[1]);
            double lon = Double.parseDouble(args[2]);

            if (!plugin.getConfigManager().validateCoordinates(lat, lon)) {
                sender.sendMessage(plugin.getConfigManager().getMessage("invalid_coordinates"));
                return;
            }

            plugin.getConfig().set("lat_origin", lat);
            plugin.getConfig().set("lon_origin", lon);
            plugin.saveConfig();

            sender.sendMessage(plugin.getConfigManager().getMessage("origin_set",
                    "lat", String.valueOf(lat),
                    "lon", String.valueOf(lon)));

        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid_coordinates"));
        }
    }

    private void handleSetScale(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin setscale <scala>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin setscale 1000");
            return;
        }

        try {
            double scale = Double.parseDouble(args[1]);

            if (!plugin.getConfigManager().validateScale(scale)) {
                sender.sendMessage(plugin.getConfigManager().getMessage("invalid_scale"));
                return;
            }

            plugin.getConfig().set("scale", scale);
            plugin.saveConfig();

            sender.sendMessage(plugin.getConfigManager().getMessage("scale_set",
                    "scale", String.valueOf(scale)));

        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getMessage("invalid_scale"));
        }
    }

    private void handleStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Statistiche Dettagliate ===");

        // Statistiche generali
        sender.sendMessage(ChatColor.YELLOW + "Ricerche totali: " + ChatColor.WHITE + statisticsManager.getTotalSearches());
        sender.sendMessage(ChatColor.YELLOW + "Teleport totali: " + ChatColor.WHITE + statisticsManager.getTotalTeleports());
        sender.sendMessage(ChatColor.YELLOW + "Cache hits: " + ChatColor.WHITE + statisticsManager.getCacheHits());
        sender.sendMessage(ChatColor.YELLOW + "API calls: " + ChatColor.WHITE + statisticsManager.getApiCalls());
        sender.sendMessage(ChatColor.YELLOW + "Cache hit rate: " + ChatColor.WHITE +
                String.format("%.1f%%", statisticsManager.getCacheHitRate()));

        // Top città cercate
        sender.sendMessage(ChatColor.GOLD + "\n=== Top 10 Città Cercate ===");
        List<Map.Entry<String, Integer>> topCities = statisticsManager.getTopCities(10);
        for (int i = 0; i < topCities.size(); i++) {
            Map.Entry<String, Integer> entry = topCities.get(i);
            sender.sendMessage(ChatColor.YELLOW.toString() + (i + 1) + ". " + ChatColor.WHITE.toString() + entry.getKey() +
                    ChatColor.GRAY.toString() + " (" + entry.getValue() + " ricerche)");

        }

        // Top giocatori (solo per admin, non per console)
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.GOLD + "\n=== Top 5 Giocatori ===");
            List<Map.Entry<String, Integer>> topPlayers = statisticsManager.getTopPlayers(5);
            for (int i = 0; i < topPlayers.size(); i++) {
                Map.Entry<String, Integer> entry = topPlayers.get(i);
                sender.sendMessage(ChatColor.YELLOW.toString() + (i + 1) + ". " + ChatColor.WHITE + entry.getKey() +
                        ChatColor.GRAY + " (" + entry.getValue() + " ricerche)");
            }
        }
    }

    private void handleNear(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin near <nome_città>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin near roma");
            return;
        }

        String cityName = args[1];
        List<String> nearCities = statisticsManager.getNearCities(cityName, 10);

        sender.sendMessage(ChatColor.GOLD + "=== Città simili a '" + cityName + "' ===");

        if (nearCities.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nessuna città simile trovata.");
            return;
        }

        for (String city : nearCities) {
            int searchCount = statisticsManager.getCitySearchCount(city);
            String message = "" + ChatColor.YELLOW + "• " + ChatColor.WHITE + city +
                    ChatColor.GRAY + " (" + searchCount + " ricerche)";
            sender.sendMessage(message);
        }
    }
}