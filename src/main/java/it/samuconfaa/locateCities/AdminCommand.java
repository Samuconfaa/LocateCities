package it.samuconfaa.locateCities;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AdminCommand implements CommandExecutor {

    private final LocateCities plugin;
    private final CityManager cityManager;

    public AdminCommand(LocateCities plugin, CityManager cityManager) {
        this.plugin = plugin;
        this.cityManager = cityManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("locatecities.reload")) {
            sender.sendMessage(ChatColor.RED + "Non hai il permesso per ricaricare la configurazione!");
            return;
        }

        plugin.getConfigManager().reload();
        sender.sendMessage(ChatColor.GREEN + "✅ Configurazione ricaricata!");
    }

    private void handleClearCache(CommandSender sender) {
        if (!sender.hasPermission("locatecities.reload")) {
            sender.sendMessage(ChatColor.RED + "Non hai il permesso per pulire la cache!");
            return;
        }

        cityManager.clearExpiredCache();
        sender.sendMessage(ChatColor.GREEN + "✅ Cache pulita!");
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
    }

    private void handleSetOrigin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("locatecities.reload")) {
            sender.sendMessage(ChatColor.RED + "Non hai il permesso per modificare l'origine!");
            return;
        }

        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin setorigin <latitudine> <longitudine>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin setorigin 41.9028 12.4964");
            return;
        }

        try {
            double lat = Double.parseDouble(args[1]);
            double lon = Double.parseDouble(args[2]);

            plugin.getConfig().set("lat_origin", lat);
            plugin.getConfig().set("lon_origin", lon);
            plugin.saveConfig();

            sender.sendMessage(ChatColor.GREEN + "✅ Origine impostata a: " + lat + ", " + lon);

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "❌ Coordinate non valide!");
        }
    }

    private void handleSetScale(CommandSender sender, String[] args) {
        if (!sender.hasPermission("locatecities.reload")) {
            sender.sendMessage(ChatColor.RED + "Non hai il permesso per modificare la scala!");
            return;
        }

        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin setscale <scala>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin setscale 1000");
            return;
        }

        try {
            double scale = Double.parseDouble(args[1]);

            if (scale <= 0) {
                sender.sendMessage(ChatColor.RED + "❌ La scala deve essere maggiore di 0!");
                return;
            }

            plugin.getConfig().set("scale", scale);
            plugin.saveConfig();

            sender.sendMessage(ChatColor.GREEN + "✅ Scala impostata a: " + scale);

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "❌ Scala non valida!");
        }
    }
}