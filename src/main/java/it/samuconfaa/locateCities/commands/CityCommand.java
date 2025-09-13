package it.samuconfaa.locateCities.commands;

import it.samuconfaa.locateCities.*;
import it.samuconfaa.locateCities.data.CityData;
import it.samuconfaa.locateCities.database.DatabaseManager;
import it.samuconfaa.locateCities.managers.CityManager;
import it.samuconfaa.locateCities.managers.EconomyManager;
import it.samuconfaa.locateCities.managers.StatisticsManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class CityCommand implements CommandExecutor {

    private final LocateCities plugin;
    private final CityManager cityManager;
    private final EconomyManager economyManager;
    private final RateLimiter rateLimiter;
    private final StatisticsManager statisticsManager;
    private final DatabaseManager databaseManager;

    public CityCommand(LocateCities plugin, CityManager cityManager, EconomyManager economyManager,
                       RateLimiter rateLimiter, StatisticsManager statisticsManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.cityManager = cityManager;
        this.economyManager = economyManager;
        this.rateLimiter = rateLimiter;
        this.statisticsManager = statisticsManager;
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "search":
                return handleSearch(sender, args);
            case "tp":
            case "teleport":
                return handleTeleport(sender, args);
            case "history":
                return handleHistory(sender);
            case "tutorial":
                return handleTutorial(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6╔══════════════════════════════════════╗");
        sender.sendMessage("§6║§e          LOCATECITIES HELP          §6║");
        sender.sendMessage("§6╠══════════════════════════════════════╣");
        sender.sendMessage("§6║§a /citta search <nome> §7- Cerca città    §6║");
        sender.sendMessage("§6║§a /citta tp <nome> §7- Teletrasportati     §6║");
        sender.sendMessage("§6║§a /citta history §7- Cronologia teleport  §6║");
        sender.sendMessage("§6║§a /citta tutorial §7- Guida interattiva   §6║");
        sender.sendMessage("§6╚══════════════════════════════════════╝");
    }

    private boolean handleSearch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Uso: /citta search <nome_città>"));
            return true;
        }

        String cityName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        return performCitySearch(sender, cityName, false);
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Uso: /citta tp <nome_città>"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Solo i giocatori possono teletrasportarsi!"));
            return true;
        }

        String cityName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        return performCitySearch(sender, cityName, true);
    }

    private boolean handleHistory(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Solo i giocatori possono vedere la cronologia!"));
            return true;
        }

        Player player = (Player) sender;
        Map<String, LocalDate> teleports = databaseManager.getPlayerTeleports(player.getName());

        player.sendMessage(plugin.getConfigManager().getMessage("teleport_history_header"));
        player.sendMessage("");

        if (teleports.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("teleport_history_empty"));
        } else {
            int index = 1;
            LocalDate today = LocalDate.now();

            for (Map.Entry<String, LocalDate> entry : teleports.entrySet()) {
                String cityName = entry.getKey();
                LocalDate teleportDate = entry.getValue();
                long daysAgo = ChronoUnit.DAYS.between(teleportDate, today);

                String formattedDate = teleportDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                player.sendMessage(plugin.getConfigManager().getMessage("teleport_history_entry",
                        "index", String.valueOf(index),
                        "city", cityName,
                        "date", formattedDate,
                        "days_ago", String.valueOf(daysAgo)));
                index++;
            }
        }

        player.sendMessage("");
        player.sendMessage(plugin.getConfigManager().getMessage("teleport_history_footer"));
        return true;
    }

    private boolean handleTutorial(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Il tutorial è disponibile solo per i giocatori!"));
            return true;
        }

        // Avvia il tutorial (delegato alla classe TutorialCommand)
        TutorialCommand tutorialCommand = new TutorialCommand(plugin);
        return tutorialCommand.onCommand(sender, null, "tutorial", new String[0]);
    }

    private boolean performCitySearch(CommandSender sender, String cityName, boolean teleport) {
        Player player = sender instanceof Player ? (Player) sender : null;

        // Controlli per i giocatori
        if (player != null) {



            if (economyManager.isEconomyEnabled()) {
                double searchCost = economyManager.getSearchCost();
                if (!economyManager.hasEnoughMoney(player, searchCost)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("insufficient_funds",
                            "cost", economyManager.formatMoney(searchCost)));
                    return true;
                }
            }

            // Controlli specifici per teleport
            if (teleport) {
                // Controllo permesso teleport
                if (!player.hasPermission("locatecities.teleport")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no_permission_teleport"));
                    return true;
                }

                // Controllo se teleport è abilitato
                if (!cityManager.configManager.isTeleportEnabled()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("teleport_disabled"));
                    return true;
                }



                // Controllo cooldown globale per teleport (qualsiasi città)
                if (plugin.getConfigManager().isTeleportDayCooldownEnabled() && !player.hasPermission("locatecities.free")) {
                    int cooldownDays = plugin.getConfigManager().getTeleportCooldownDays();
                    if (!databaseManager.canTeleport(player.getName(), cooldownDays)) {
                        int remainingDays = databaseManager.getRemainingDays(player.getName(), cooldownDays);

                        LocalDate lastTeleport = databaseManager.getLastTeleportDate(player.getName());
                        String lastCity = databaseManager.getLastTeleportCity(player.getName());
                        String lastDateStr = lastTeleport != null ?
                                lastTeleport.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Mai";

                        player.sendMessage(plugin.getConfigManager().getMessage("teleport_global_cooldown",
                                "days", String.valueOf(remainingDays),
                                "last_city", lastCity != null ? lastCity : "Sconosciuta",
                                "last_date", lastDateStr));
                        return true;
                    }
                }
            }
        }

        // Messaggio di ricerca
        sender.sendMessage(plugin.getConfigManager().getMessage("searching", "city", cityName));

        // Esegui la ricerca
        cityManager.findCity(cityName).whenComplete((cityData, throwable) -> {
            // Torna al main thread per sicurezza
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (throwable != null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                            "error", throwable.getMessage()));
                    return;
                }

                // Addebita il costo della ricerca
                if (player != null && economyManager.isEconomyEnabled()) {
                    double searchCost = economyManager.getSearchCost();
                    if (economyManager.chargeMoney(player, searchCost, "City search")) {
                        player.sendMessage(plugin.getConfigManager().getMessage("economy_charged",
                                "cost", economyManager.formatMoney(searchCost)));
                    }
                }

                CityData.MinecraftCoordinates coords = cityData.toMinecraftCoordinates(cityManager.configManager);

                // Messaggio con le coordinate
                sender.sendMessage(plugin.getConfigManager().getMessage("found",
                        "city", cityData.getName(),
                        "x", String.valueOf(coords.getX()),
                        "z", String.valueOf(coords.getZ())));

                // Registra la ricerca nelle statistiche
                statisticsManager.recordSearch(cityName, player, false); // TODO: determinare se da cache

                // Se è richiesto il teleport
                if (teleport && player != null) {
                    handleTeleportExecution(player, cityData, coords);
                } else if (!teleport && player != null && cityManager.configManager.isTeleportEnabled()) {
                    // Suggerisci il teleport se disponibile
                    player.sendMessage("§7💡 Usa '§a/citta tp " + cityName + "§7' per teletrasportarti!");
                }
            });
        });

        return true;
    }

    private void handleTeleportExecution(Player player, CityData cityData, CityData.MinecraftCoordinates coords) {
        Location playerLocation = player.getLocation();
        Location cityLocation = cityManager.getMinecraftLocation(cityData, player.getWorld());

        // Calcola distanza
        double distance = playerLocation.distance(cityLocation);
        boolean isFree = distance <= economyManager.getFreeDistance();

        // Calcola il costo del teleport (semplificato, senza opzione bypass pagamento)
        double teleportCost = calculateTeleportCost(player, distance, isFree);

        // Controllo economy per teleport (se non è gratuito)
        if (teleportCost > 0 && economyManager.isEconomyEnabled()) {
            if (!economyManager.hasEnoughMoney(player, teleportCost)) {
                player.sendMessage(plugin.getConfigManager().getMessage("insufficient_funds",
                        "cost", economyManager.formatMoney(teleportCost)));
                return;
            }

            if (economyManager.chargeMoney(player, teleportCost, "City teleport")) {
                player.sendMessage(plugin.getConfigManager().getMessage("teleport_charged",
                        "cost", economyManager.formatMoney(teleportCost)));
            }
        } else if (isFree && teleportCost == 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("free_teleport",
                    "distance", String.valueOf((int) distance)));
        }

        // Esegui il teletrasporto
        player.teleport(cityLocation);
        player.sendMessage(plugin.getConfigManager().getMessage("teleported", "city", cityData.getName()));

        // Registra il teleport nelle statistiche e nel database
        statisticsManager.recordTeleport();
        databaseManager.recordTeleport(player.getName(), cityData.getName());
    }

    private double calculateTeleportCost(Player player, double distance, boolean isFree) {
        // Se ha il permesso gratuito o è entro la distanza gratuita
        if (player.hasPermission("locatecities.free") || isFree) {
            return 0.0;
        }

        // Costo fisso del teleport (senza bonus per giorni)
        return economyManager.getTeleportCost();
    }
}