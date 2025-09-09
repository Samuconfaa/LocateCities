package it.samuconfaa.locateCities;

import org.bukkit.plugin.java.JavaPlugin;

public class LocateCities extends JavaPlugin {

    private CityManager cityManager;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private RateLimiter rateLimiter;
    private StatisticsManager statisticsManager;

    @Override
    public void onEnable() {
        // Salva la configurazione di default se non esiste
        saveDefaultConfig();

        // Inizializza i manager nell'ordine corretto
        configManager = new ConfigManager(this);
        economyManager = new EconomyManager(this);
        rateLimiter = new RateLimiter(configManager);
        statisticsManager = new StatisticsManager(this);
        cityManager = new CityManager(this, configManager);

        // Registra i comandi
        getCommand("citta").setExecutor(new CityCommand(this, cityManager, economyManager, rateLimiter, statisticsManager));
        getCommand("citta").setTabCompleter(new CityTabCompleter());
        getCommand("cittaadmin").setExecutor(new AdminCommand(this, cityManager, statisticsManager));

        // Task per pulire la cache scaduta ogni ora
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            cityManager.clearExpiredCache();
        }, 20L * 3600L, 20L * 3600L); // 1 ora = 3600 secondi = 72000 tick

        // Task per salvare le statistiche ogni 10 minuti
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (statisticsManager != null) {
                statisticsManager.saveStatistics();
            }
        }, 20L * 600L, 20L * 600L); // 10 minuti

        getLogger().info("LocateCities plugin abilitato!");
        getLogger().info("Database offline contiene " + OfflineCityDatabase.getCityCount() + " città");

        if (economyManager.isEconomyEnabled()) {
            getLogger().info("Economy abilitata - Costo ricerca: $" + economyManager.getSearchCost() +
                    ", Costo teleport: $" + economyManager.getTeleportCost());
        }

        if (configManager.isRateLimitEnabled()) {
            getLogger().info("Rate limiting abilitato - Ricerca: " + configManager.getSearchCooldown() +
                    "s, Teleport: " + configManager.getTeleportCooldown() + "s");
        }
    }

    @Override
    public void onDisable() {
        // Salva cache e statistiche
        if (cityManager != null) {
            cityManager.saveCache();
        }
        if (statisticsManager != null) {
            statisticsManager.saveStatistics();
        }

        getLogger().info("LocateCities plugin disabilitato!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public CityManager getCityManager() {
        return cityManager;
    }
}