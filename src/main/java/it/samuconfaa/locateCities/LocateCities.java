package it.samuconfaa.locateCities;

import it.samuconfaa.locateCities.commands.AdminCommand;
import it.samuconfaa.locateCities.commands.AdminTabCompleter;
import it.samuconfaa.locateCities.commands.CityCommand;
import it.samuconfaa.locateCities.commands.CityTabCompleter;
import it.samuconfaa.locateCities.database.DatabaseManager;
import it.samuconfaa.locateCities.database.OfflineCityDatabase;
import it.samuconfaa.locateCities.managers.CityManager;
import it.samuconfaa.locateCities.managers.ConfigManager;
import it.samuconfaa.locateCities.managers.EconomyManager;
import it.samuconfaa.locateCities.managers.StatisticsManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LocateCities extends JavaPlugin {

    private CityManager cityManager;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private RateLimiter rateLimiter;
    private StatisticsManager statisticsManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // Salva la configurazione di default se non esiste
        saveDefaultConfig();

        // Inizializza i manager nell'ordine corretto
        configManager = new ConfigManager(this);
        economyManager = new EconomyManager(this);
        rateLimiter = new RateLimiter(configManager);
        statisticsManager = new StatisticsManager(this);
        databaseManager = new DatabaseManager(this);
        cityManager = new CityManager(this, configManager);

        // Registra i comandi con i nuovi tab completers
        getCommand("citta").setExecutor(new CityCommand(this, cityManager, economyManager, rateLimiter, statisticsManager, databaseManager));
        getCommand("citta").setTabCompleter(new CityTabCompleter());

        getCommand("cittaadmin").setExecutor(new AdminCommand(this, cityManager, statisticsManager));
        getCommand("cittaadmin").setTabCompleter(new AdminTabCompleter());

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

        // Task per pulire i record vecchi dal database ogni giorno
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (databaseManager != null && configManager.isTeleportDayCooldownEnabled()) {
                // Mantieni i record per il doppio del periodo di cooldown + 30 giorni di buffer
                int daysToKeep = (configManager.getTeleportCooldownDays() * 2) + 30;
                databaseManager.clearOldTeleports(daysToKeep);
            }
        }, 20L * 86400L, 20L * 86400L); // 1 giorno = 86400 secondi

        getLogger().info("LocateCities plugin abilitato!");
        getLogger().info("Database offline contiene " + OfflineCityDatabase.getCityCount() + " città");

        if (economyManager.isEconomyEnabled()) {
            getLogger().info("Economy abilitata - Costo ricerca: $" + economyManager.getSearchCost() +
                    ", Costo teleport: $" + economyManager.getTeleportCost());
        }

        if (configManager.isTeleportDayCooldownEnabled()) {
            getLogger().info("Sistema cooldown giorni attivo - Ogni " + configManager.getTeleportCooldownDays() +
                    " giorni (cooldown globale per qualsiasi città)");
        }

        if (configManager.isRateLimitEnabled()) {
            getLogger().info("Rate limiting abilitato - Ricerca: " + configManager.getSearchCooldown() +
                    "s, Teleport: " + configManager.getTeleportCooldown() + "s");
        }

        getLogger().info("Nuovi comandi disponibili:");
        getLogger().info("- /citta search <nome> - Cerca una città");
        getLogger().info("- /citta tp <nome> - Teletrasportati");
        getLogger().info("- /citta history - Cronologia teleport");
        getLogger().info("- /citta tutorial - Tutorial interattivo");
        getLogger().info("Tab completion abilitato per tutti i comandi!");
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
        if (databaseManager != null) {
            databaseManager.close();
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

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}