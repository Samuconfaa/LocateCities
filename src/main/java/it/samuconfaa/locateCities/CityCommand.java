package it.samuconfaa.locateCities;

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
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Uso: /citta <nome> [tp] | /citta history | /citta tutorial"));
            return true;
        }

        // Sottcomando history
        if (args[0].equalsIgnoreCase("history")) {
            return handleHistory(sender);
        }

        // Sottomando tutorial
        if (args[0].equalsIgnoreCase("tutorial")) {
            return handleTutorial(sender);
        }

        String cityName = args[0];
        boolean teleport = args.length > 1 && args[1].equalsIgnoreCase("tp");

        // Controlla se il teleport è richiesto ma il sender non è un player
        if (teleport && !(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Solo i giocatori possono teletrasportarsi!"));
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;

        // Controlli per i giocatori
        if (player != null) {
            // Rate limiting per ricerca
            if (!rateLimiter.canSearch(player)) {
                int remaining = rateLimiter.getRemainingSearchTime(player);
                player.sendMessage(plugin.getConfigManager().getMessage("rate_limited_search",
                        "seconds", String.valueOf(remaining)));
                return true;
            }

            // Controllo economy per ricerca
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

                // Rate limiting per teleport
                if (!rateLimiter.canTeleport(player)) {
                    int remaining = rateLimiter.getRemainingTeleportTime(player);
                    player.sendMessage(plugin.getConfigManager().getMessage("rate_limited_teleport",
                            "seconds", String.valueOf(remaining)));
                    return true;
                }

                // Controllo cooldown giorni per teleport
                if (plugin.getConfigManager().isTeleportDayCooldownEnabled()) {
                    int cooldownDays = plugin.getConfigManager().getTeleportCooldownDays();
                    if (!databaseManager.canTeleportToCity(player.getName(), cityName, cooldownDays)) {
                        int remainingDays = databaseManager.getRemainingDays(player.getName(), cityName, cooldownDays);

                        // Mostra info sul costo per ridurre l'attesa
                        if (plugin.getConfigManager().getTeleportCostPerDay() > 0) {
                            player.sendMessage(plugin.getConfigManager().getMessage("teleport_cost_per_day",
                                    "cost_per_day", String.valueOf(plugin.getConfigManager().getTeleportCostPerDay())));
                        }

                        Map<String, LocalDate> teleports = databaseManager.getPlayerTeleports(player.getName());
                        LocalDate lastTeleport = teleports.get(cityName.toLowerCase());
                        String lastDateStr = lastTeleport != null ?
                                lastTeleport.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Mai";

                        player.sendMessage(plugin.getConfigManager().getMessage("teleport_day_cooldown",
                                "city", cityName,
                                "days", String.valueOf(remainingDays),
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
                    handleTeleport(player, cityData, coords);
                } else if (!teleport && player != null && cityManager.configManager.isTeleportEnabled()) {
                    // Suggerisci il teleport se disponibile
                    player.sendMessage("§7💡 Usa '/citta " + cityName + " tp' per teletrasportarti!");
                }
            });
        });

        return true;
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

    private void handleTeleport(Player player, CityData cityData, CityData.MinecraftCoordinates coords) {
        Location playerLocation = player.getLocation();
        Location cityLocation = cityManager.getMinecraftLocation(cityData, player.getWorld());

        // Calcola distanza
        double distance = playerLocation.distance(cityLocation);
        boolean isFree = distance <= economyManager.getFreeDistance();

        // Calcola il costo del teleport con il nuovo sistema
        double teleportCost = calculateTeleportCost(player, cityData.getName(), distance, isFree);

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

    private double calculateTeleportCost(Player player, String cityName, double distance, boolean isFree) {
        // Se ha il permesso gratuito o è entro la distanza gratuita
        if (player.hasPermission("locatecities.free") || isFree) {
            return 0.0;
        }

        // Se il sistema per giorni non è abilitato, usa il costo fisso
        if (!plugin.getConfigManager().isTeleportDayCooldownEnabled()) {
            return economyManager.getTeleportCost();
        }

        // Calcola il costo basato sui giorni dal ultimo teleport
        int cooldownDays = plugin.getConfigManager().getTeleportCooldownDays();
        int remainingDays = databaseManager.getRemainingDays(player.getName(), cityName, cooldownDays);

        if (remainingDays == 0) {
            // Può teletrasportarsi normalmente
            return economyManager.getTeleportCost();
        } else {
            // Costo aumentato per saltare l'attesa
            double costPerDay = plugin.getConfigManager().getTeleportCostPerDay();
            return economyManager.getTeleportCost() + (costPerDay * remainingDays);
        }
    }
}