package it.samuconfaa.locateCities.commands;

import it.samuconfaa.locateCities.managers.CityManager;
import it.samuconfaa.locateCities.LocateCities;
import it.samuconfaa.locateCities.managers.StatisticsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

            case "playerhistory":
                handlePlayerHistory(sender, args);
                break;

            case "cleandb":
                handleCleanDatabase(sender, args);
                break;

            case "dbstats":
                handleDatabaseStats(sender);
                break;

            case "bypass":
                handleBypassCooldown(sender, args);
                break;

            case "setworld":
                handleSetWorld(sender, args);
                break;

            // NUOVO: Comando per gestire permessi VIP
            case "vip":
                handleVipManagement(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔═══════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.YELLOW + "        LOCATECITIES ADMIN HELP        " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "╠═══════════════════════════════════════╣");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.GREEN + " /cittaadmin reload" + ChatColor.GRAY + " - Ricarica config    " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.GREEN + " /cittaadmin clearcache" + ChatColor.GRAY + " - Pulisce cache" + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.GREEN + " /cittaadmin info" + ChatColor.GRAY + " - Info plugin        " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.GREEN + " /cittaadmin stats" + ChatColor.GRAY + " - Statistiche       " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.GREEN + " /cittaadmin dbstats" + ChatColor.GRAY + " - Stats database   " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.GREEN + " /cittaadmin bypass <player> <city>" + ChatColor.GRAY + " - Bypass cooldown" + ChatColor.GOLD + "║");

        // NUOVO: Comando VIP se il sistema è abilitato
        if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
            sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.AQUA + " /cittaadmin vip <check|info> <player>" + ChatColor.GRAY + " - Gestione VIP" + ChatColor.GOLD + "║");
        }

        sender.sendMessage(ChatColor.GOLD + "╠═══════════════════════════════════════╣");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.AQUA + " /cittaadmin setorigin <lat> <lon>     " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.AQUA + " /cittaadmin setscale <scala>          " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.AQUA + " /cittaadmin near <città>              " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.AQUA + " /cittaadmin setworld <mondo>          " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.AQUA + " /cittaadmin playerhistory <player>    " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.AQUA + " /cittaadmin cleandb [giorni]          " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "╚═══════════════════════════════════════╝");
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
        sender.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.YELLOW + "         LOCATECITIES INFO 📊          " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        sender.sendMessage("");

        // Informazioni base
        sender.sendMessage(ChatColor.AQUA + "📋 " + ChatColor.WHITE + "INFORMAZIONI BASE:");
        sender.sendMessage(ChatColor.YELLOW + "   Versione: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "   Origine mappa: " + ChatColor.WHITE +
                String.format("%.4f, %.4f",
                        plugin.getConfigManager().getLatOrigin(),
                        plugin.getConfigManager().getLonOrigin()));
        sender.sendMessage(ChatColor.YELLOW + "   Scala: " + ChatColor.WHITE + plugin.getConfigManager().getScale());
        sender.sendMessage("");

        // Funzionalità
        sender.sendMessage(ChatColor.AQUA + "⚙️ " + ChatColor.WHITE + "FUNZIONALITÀ:");
        sender.sendMessage(ChatColor.YELLOW + "   Teleport: " + ChatColor.WHITE +
                (plugin.getConfigManager().isTeleportEnabled() ? ChatColor.GREEN + "✅ Abilitato" : ChatColor.RED + "❌ Disabilitato"));
        sender.sendMessage(ChatColor.YELLOW + "   Economy: " + ChatColor.WHITE +
                (plugin.getConfigManager().isEconomyEnabled() ? ChatColor.GREEN + "✅ Abilitata" : ChatColor.RED + "❌ Disabilitata"));
        sender.sendMessage(ChatColor.YELLOW + "   Rate Limiting: " + ChatColor.WHITE +
                (plugin.getConfigManager().isRateLimitEnabled() ? ChatColor.GREEN + "✅ Abilitato" : ChatColor.RED + "❌ Disabilitato"));

        // NUOVO: Info sistema VIP
        if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "   Sistema VIP: " + ChatColor.WHITE + ChatColor.GREEN + "✅ Abilitato");
            sender.sendMessage(ChatColor.YELLOW + "   Permesso VIP: " + ChatColor.WHITE + plugin.getConfigManager().getVipTeleportPermission());
            sender.sendMessage(ChatColor.YELLOW + "   Cooldown VIP: " + ChatColor.WHITE + plugin.getConfigManager().getVipTeleportCooldownDays() + " giorni");
            sender.sendMessage(ChatColor.YELLOW + "   Altri solo ricerca: " + ChatColor.WHITE +
                    (plugin.getConfigManager().allowOthersSearchOnly() ? ChatColor.GREEN + "✅ Sì" : ChatColor.RED + "❌ No"));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "   Sistema VIP: " + ChatColor.WHITE + ChatColor.RED + "❌ Disabilitato");
            sender.sendMessage(ChatColor.YELLOW + "   Cooldown Giorni: " + ChatColor.WHITE +
                    (plugin.getConfigManager().isTeleportDayCooldownEnabled() ?
                            ChatColor.GREEN + "✅ " + plugin.getConfigManager().getTeleportCooldownDays() + " giorni" :
                            ChatColor.RED + "❌ Disabilitato"));
        }

        sender.sendMessage("");

        // Statistiche rapide
        sender.sendMessage(ChatColor.AQUA + "📊 " + ChatColor.WHITE + "STATISTICHE RAPIDE:");
        sender.sendMessage(ChatColor.YELLOW + "   Ricerche totali: " + ChatColor.WHITE + statisticsManager.getTotalSearches());
        sender.sendMessage(ChatColor.YELLOW + "   Teleport totali: " + ChatColor.WHITE + statisticsManager.getTotalTeleports());
        sender.sendMessage(ChatColor.YELLOW + "   Cache hit rate: " + ChatColor.WHITE +
                String.format("%.1f%%", statisticsManager.getCacheHitRate()));
        sender.sendMessage(ChatColor.YELLOW + "   Città in cache: " + ChatColor.WHITE + cityManager.getCacheSize());

        sender.sendMessage("");


        sender.sendMessage(ChatColor.YELLOW + "   Mondo target: " + ChatColor.WHITE +
                plugin.getConfigManager().getTargetWorldName());

        String targetWorldName = plugin.getConfigManager().getTargetWorldName();
        World targetWorld = plugin.getServer().getWorld(targetWorldName);
        if (targetWorld != null) {
            sender.sendMessage(ChatColor.YELLOW + "   Stato mondo: " + ChatColor.GREEN + "✅ Esistente");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "   Stato mondo: " + ChatColor.RED + "❌ Non trovato");
            sender.sendMessage(ChatColor.RED + "   ⚠️ I teleport useranno il mondo principale come fallback");
        }
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
        sender.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.YELLOW + "      📊 STATISTICHE DETTAGLIATE 📊     " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        sender.sendMessage("");

        // Statistiche generali
        sender.sendMessage(ChatColor.AQUA + "📈 " + ChatColor.WHITE + "STATISTICHE GENERALI:");
        sender.sendMessage(ChatColor.YELLOW + "   Ricerche totali: " + ChatColor.WHITE + statisticsManager.getTotalSearches());
        sender.sendMessage(ChatColor.YELLOW + "   Teleport totali: " + ChatColor.WHITE + statisticsManager.getTotalTeleports());
        sender.sendMessage(ChatColor.YELLOW + "   Cache hits: " + ChatColor.WHITE + statisticsManager.getCacheHits());
        sender.sendMessage(ChatColor.YELLOW + "   API calls: " + ChatColor.WHITE + statisticsManager.getApiCalls());
        sender.sendMessage(ChatColor.YELLOW + "   Cache hit rate: " + ChatColor.WHITE +
                String.format("%.1f%%", statisticsManager.getCacheHitRate()));
        sender.sendMessage("");

        // Top città cercate
        sender.sendMessage(ChatColor.AQUA + "🏙️ " + ChatColor.WHITE + "TOP 10 CITTÀ CERCATE:");
        List<Map.Entry<String, Integer>> topCities = statisticsManager.getTopCities(10);
        if (topCities.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "   Nessuna città cercata ancora.");
        } else {
            for (int i = 0; i < topCities.size(); i++) {
                Map.Entry<String, Integer> entry = topCities.get(i);
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "📍";
                sender.sendMessage(ChatColor.WHITE + "   " + medal + " " + (i + 1) + ". " +
                        ChatColor.AQUA + entry.getKey() + ChatColor.GRAY + " (" + entry.getValue() + " ricerche)");
            }
        }
        sender.sendMessage("");

        // Top giocatori (solo per admin, non per console)
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.AQUA + "👥 " + ChatColor.WHITE + "TOP 5 GIOCATORI:");
            List<Map.Entry<String, Integer>> topPlayers = statisticsManager.getTopPlayers(5);
            if (topPlayers.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "   Nessun giocatore ha fatto ricerche ancora.");
            } else {
                for (int i = 0; i < topPlayers.size(); i++) {
                    Map.Entry<String, Integer> entry = topPlayers.get(i);
                    String medal = i == 0 ? "👑" : i == 1 ? "🥈" : i == 2 ? "🥉" : "👤";
                    sender.sendMessage(ChatColor.WHITE + "   " + medal + " " + (i + 1) + ". " +
                            ChatColor.GREEN + entry.getKey() + ChatColor.GRAY + " (" + entry.getValue() + " ricerche)");
                }
            }
        }
    }

    private void handleSetWorld(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin setworld <nome_mondo>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin setworld survival");

            // Mostra mondi disponibili
            sender.sendMessage(ChatColor.GRAY + "Mondi disponibili:");
            for (World world : plugin.getServer().getWorlds()) {
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + world.getName());
            }
            return;
        }

        String worldName = args[1].trim();

        // Validazione nome mondo
        if (!worldName.matches("^[a-zA-Z0-9_-]{1,50}$")) {
            sender.sendMessage(ChatColor.RED + "Nome mondo non valido! Usa solo lettere, numeri, underscore e trattini.");
            return;
        }

        // Verifica se il mondo esiste
        World targetWorld = plugin.getServer().getWorld(worldName);
        if (targetWorld == null) {
            sender.sendMessage(ChatColor.RED + "❌ Il mondo '" + worldName + "' non esiste!");
            sender.sendMessage(ChatColor.GRAY + "Mondi disponibili:");
            for (World world : plugin.getServer().getWorlds()) {
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + world.getName());
            }
            return;
        }

        // Salva la configurazione
        plugin.getConfig().set("target_world", worldName);
        plugin.saveConfig();

        sender.sendMessage(ChatColor.GREEN + "✅ Mondo target impostato a: " + ChatColor.WHITE + worldName);
        sender.sendMessage(ChatColor.GRAY + "Tutti i futuri teletrasporti avverranno in questo mondo.");

        // Log per admin
        plugin.getLogger().info(sender.getName() + " ha impostato il mondo target a: " + worldName);
    }

    private void handleNear(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin near <nome_città>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin near roma");
            return;
        }

        String cityName = args[1];
        List<String> nearCities = statisticsManager.getNearCities(cityName, 10);

        sender.sendMessage(ChatColor.GOLD + "🔍 " + ChatColor.WHITE + "Città simili a '" +
                ChatColor.AQUA + cityName + ChatColor.WHITE + "':");
        sender.sendMessage("");

        if (nearCities.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "❌ Nessuna città simile trovata.");
            return;
        }

        for (int i = 0; i < nearCities.size(); i++) {
            String city = nearCities.get(i);
            int searchCount = statisticsManager.getCitySearchCount(city);
            sender.sendMessage(
                    ChatColor.WHITE + "" + (i + 1) + ". "
                            + ChatColor.YELLOW + city
                            + ChatColor.GRAY + " (" + searchCount + " ricerche)"
            );
        }
    }

    private void handlePlayerHistory(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin playerhistory <nome_giocatore>");
            return;
        }

        String playerName = args[1];
        Map<String, LocalDate> teleports = plugin.getDatabaseManager().getPlayerTeleports(playerName);

        sender.sendMessage(ChatColor.GOLD + "📜 " + ChatColor.WHITE + "Cronologia teleport di " +
                ChatColor.GREEN + playerName + ChatColor.WHITE + ":");
        sender.sendMessage("");

        if (teleports.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "❌ " + playerName + " non ha mai effettuato teleport.");
            return;
        }

        LocalDate today = LocalDate.now();
        int index = 1;
        for (Map.Entry<String, LocalDate> entry : teleports.entrySet()) {
            String cityName = entry.getKey();
            LocalDate teleportDate = entry.getValue();
            long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(teleportDate, today);

            String formattedDate = teleportDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

            sender.sendMessage(ChatColor.WHITE + "" + index + ". " + ChatColor.AQUA + cityName
                    + ChatColor.GRAY + " - " + formattedDate + " (" + daysAgo + " giorni fa)");
            index++;
        }
    }

    private void handleCleanDatabase(CommandSender sender, String[] args) {
        int daysToKeep = 90; // Default: mantieni 90 giorni

        if (args.length == 2) {
            try {
                daysToKeep = Integer.parseInt(args[1]);
                if (daysToKeep < 1) {
                    sender.sendMessage(ChatColor.RED + "Il numero di giorni deve essere positivo!");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Numero di giorni non valido!");
                return;
            }
        }

        plugin.getDatabaseManager().clearOldTeleports(daysToKeep);
        sender.sendMessage(ChatColor.GREEN + "🗑️ Database pulito! Eliminati i record più vecchi di " +
                daysToKeep + " giorni.");
    }

    private void handleDatabaseStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "📊 " + ChatColor.WHITE + "STATISTICHE DATABASE:");
        sender.sendMessage(ChatColor.YELLOW + "   Database: " + ChatColor.WHITE + "SQLite (teleports.db)");

        if (plugin.getConfigManager().isVipTeleportSystemEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "   Sistema VIP: " + ChatColor.GREEN + "✅ Attivo");
            sender.sendMessage(ChatColor.YELLOW + "   Cooldown VIP: " + ChatColor.WHITE +
                    plugin.getConfigManager().getVipTeleportCooldownDays() + " giorni");
            sender.sendMessage(ChatColor.YELLOW + "   Permesso VIP: " + ChatColor.WHITE +
                    plugin.getConfigManager().getVipTeleportPermission());
        } else if (plugin.getConfigManager().isTeleportDayCooldownEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "   Sistema cooldown: " + ChatColor.GREEN + "✅ Attivo (" +
                    plugin.getConfigManager().getTeleportCooldownDays() + " giorni)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "   Sistema cooldown: " + ChatColor.RED + "❌ Disattivo");
        }

        sender.sendMessage(ChatColor.GRAY + "   Usa '/cittaadmin cleandb [giorni]' per pulire vecchi record.");
        sender.sendMessage(ChatColor.GRAY + "   Usa '/cittaadmin bypass <player> <city>' per bypassare il cooldown.");
    }

    private void handleBypassCooldown(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin bypass <nome_giocatore> <nome_città>");
            sender.sendMessage(ChatColor.GRAY + "Esempio: /cittaadmin bypass Steve roma");
            return;
        }

        String playerName = args[1];
        String cityName = args[2];

        // Controlla se il giocatore esiste (online o offline)
        Player target = Bukkit.getPlayer(playerName);
        if (target == null && Bukkit.getOfflinePlayer(playerName).getName() == null) {
            sender.sendMessage(ChatColor.RED + "❌ Giocatore non trovato: " + playerName);
            return;
        }

        // Registra un teleport "fittizio" con data odierna per resettare il cooldown
        plugin.getDatabaseManager().recordTeleport(playerName, cityName);

        sender.sendMessage(ChatColor.GREEN + "✅ Cooldown bypassato per " + ChatColor.YELLOW + playerName +
                ChatColor.GREEN + " verso " + ChatColor.AQUA + cityName + ChatColor.GREEN + "!");

        // Notifica il giocatore se è online
        if (target != null) {
            target.sendMessage(plugin.getConfigManager().getMessage("cooldown_bypassed", "city", cityName));
        }

        plugin.getLogger().info(sender.getName() + " ha bypassato il cooldown di " + playerName + " per " + cityName);
    }

    // NUOVO: Gestione VIP
    private void handleVipManagement(CommandSender sender, String[] args) {
        if (!plugin.getConfigManager().isVipTeleportSystemEnabled()) {
            sender.sendMessage(ChatColor.RED + "❌ Il sistema VIP non è abilitato!");
            sender.sendMessage(ChatColor.GRAY + "Abilita 'teleport_permission_system.enabled: true' nel config.yml");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin vip <check|info> [giocatore]");
            sender.sendMessage(ChatColor.GRAY + "- check <giocatore> - Verifica status VIP");
            sender.sendMessage(ChatColor.GRAY + "- info - Mostra info sistema VIP");
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "check":
                handleVipCheck(sender, args);
                break;
            case "info":
                handleVipInfo(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Sottcomando non valido: " + subCommand);
                break;
        }
    }

    private void handleVipCheck(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /cittaadmin vip check <nome_giocatore>");
            return;
        }

        String playerName = args[2];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "❌ Giocatore non trovato online: " + playerName);
            return;
        }

        String vipPermission = plugin.getConfigManager().getVipTeleportPermission();
        boolean hasVipAccess = target.hasPermission(vipPermission) ||
                target.hasPermission("locatecities.admin") ||
                target.hasPermission("locatecities.free");

        sender.sendMessage(ChatColor.GOLD + "🔍 " + ChatColor.WHITE + "Status VIP di " + ChatColor.YELLOW + playerName + ":");
        sender.sendMessage("");

        if (hasVipAccess) {
            sender.sendMessage(ChatColor.GREEN + "✅ " + ChatColor.WHITE + "Ha accesso VIP al teletrasporto");
            sender.sendMessage(ChatColor.GRAY + "   Permesso: " + ChatColor.GREEN + vipPermission);

            // Mostra info cooldown
            int cooldownDays = plugin.getConfigManager().getVipTeleportCooldownDays();
            LocalDate lastTeleport = plugin.getDatabaseManager().getLastTeleportDate(playerName);

            if (lastTeleport != null) {
                long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(lastTeleport, LocalDate.now());
                boolean canTeleport = plugin.getDatabaseManager().canTeleport(playerName, cooldownDays);

                sender.sendMessage(ChatColor.GRAY + "   Ultimo teleport: " + ChatColor.WHITE +
                        lastTeleport.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                        " (" + daysAgo + " giorni fa)");

                if (canTeleport) {
                    sender.sendMessage(ChatColor.GREEN + "   ✅ Può teletrasportarsi ora");
                } else {
                    int remaining = plugin.getDatabaseManager().getRemainingDays(playerName, cooldownDays);
                    sender.sendMessage(ChatColor.RED + "   ❌ Cooldown attivo - " + remaining + " giorni rimanenti");
                }
            } else {
                sender.sendMessage(ChatColor.GRAY + "   Nessun teleport precedente");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "❌ " + ChatColor.WHITE + "NON ha accesso VIP al teletrasporto");
            sender.sendMessage(ChatColor.GRAY + "   Permesso richiesto: " + ChatColor.YELLOW + vipPermission);
        }
    }

    private void handleVipInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        sender.sendMessage(ChatColor.GOLD + "║" + ChatColor.YELLOW + "        🌟 INFO SISTEMA VIP 🌟         " + ChatColor.GOLD + "║");
        sender.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.AQUA + "📋 " + ChatColor.WHITE + "CONFIGURAZIONE:");
        sender.sendMessage(ChatColor.YELLOW + "   Sistema abilitato: " + ChatColor.GREEN + "✅ Sì");
        sender.sendMessage(ChatColor.YELLOW + "   Permesso richiesto: " + ChatColor.WHITE +
                plugin.getConfigManager().getVipTeleportPermission());
        sender.sendMessage(ChatColor.YELLOW + "   Cooldown: " + ChatColor.WHITE +
                plugin.getConfigManager().getVipTeleportCooldownDays() + " giorni");
        sender.sendMessage(ChatColor.YELLOW + "   Altri possono cercare: " + ChatColor.WHITE +
                (plugin.getConfigManager().allowOthersSearchOnly() ? ChatColor.GREEN + "✅ Sì" : ChatColor.RED + "❌ No"));
        sender.sendMessage("");

        sender.sendMessage(ChatColor.AQUA + "👥 " + ChatColor.WHITE + "UTENTI VIP ONLINE:");
        boolean foundVips = false;
        String vipPermission = plugin.getConfigManager().getVipTeleportPermission();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(vipPermission) || player.hasPermission("locatecities.admin") || player.hasPermission("locatecities.free")) {
                if (!foundVips) {
                    foundVips = true;
                }
                sender.sendMessage(ChatColor.WHITE + "   🌟 " + ChatColor.GREEN + player.getName());
            }
        }

        if (!foundVips) {
            sender.sendMessage(ChatColor.GRAY + "   Nessun utente VIP online al momento");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Usa '/cittaadmin vip check <giocatore>' per verificare un utente specifico");
    }
}