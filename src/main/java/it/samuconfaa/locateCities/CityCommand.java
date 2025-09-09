package it.samuconfaa.locateCities;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CityCommand implements CommandExecutor {

    private final LocateCities plugin;
    private final CityManager cityManager;
    private final EconomyManager economyManager;
    private final RateLimiter rateLimiter;
    private final StatisticsManager statisticsManager;

    public CityCommand(LocateCities plugin, CityManager cityManager, EconomyManager economyManager,
                       RateLimiter rateLimiter, StatisticsManager statisticsManager) {
        this.plugin = plugin;
        this.cityManager = cityManager;
        this.economyManager = economyManager;
        this.rateLimiter = rateLimiter;
        this.statisticsManager = statisticsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("error_general",
                    "error", "Uso: /citta <nome> [tp]"));
            return true;
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

    private void handleTeleport(Player player, CityData cityData, CityData.MinecraftCoordinates coords) {
        Location playerLocation = player.getLocation();
        Location cityLocation = cityManager.getMinecraftLocation(cityData, player.getWorld());

        // Calcola distanza
        double distance = playerLocation.distance(cityLocation);
        boolean isFree = distance <= economyManager.getFreeDistance();

        // Controllo economy per teleport (se non è gratuito)
        if (!isFree && economyManager.isEconomyEnabled()) {
            double teleportCost = economyManager.getTeleportCost();
            if (!economyManager.hasEnoughMoney(player, teleportCost)) {
                player.sendMessage(plugin.getConfigManager().getMessage("insufficient_funds",
                        "cost", economyManager.formatMoney(teleportCost)));
                return;
            }

            if (economyManager.chargeMoney(player, teleportCost, "City teleport")) {
                player.sendMessage(plugin.getConfigManager().getMessage("teleport_charged",
                        "cost", economyManager.formatMoney(teleportCost)));
            }
        } else if (isFree) {
            player.sendMessage(plugin.getConfigManager().getMessage("free_teleport",
                    "distance", String.valueOf((int) distance)));
        }

        // Esegui il teletrasporto
        player.teleport(cityLocation);
        player.sendMessage(plugin.getConfigManager().getMessage("teleported", "city", cityData.getName()));

        // Registra il teleport nelle statistiche
        statisticsManager.recordTeleport();
    }
}