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

import java.util.logging.Level;
import java.util.logging.Logger;

public class LocateCities extends JavaPlugin {

    private CityManager cityManager;
    private ConfigManager configManager;
    private EconomyManager economyManager;
    private RateLimiter rateLimiter;
    private StatisticsManager statisticsManager;
    private DatabaseManager databaseManager;

    private boolean pluginInitialized = false;
    private final Logger logger = getLogger();

    @Override
    public void onEnable() {
        try {
            initializePlugin();
            registerCommands();
            scheduleTasks();
            logStartupInfo();

            pluginInitialized = true;
            logger.info("LocateCities plugin abilitato con successo!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore critico durante l'inizializzazione del plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializePlugin() {
        try {
            // Salva la configurazione di default se non esiste
            saveDefaultConfig();

            // Inizializza i manager nell'ordine corretto con error handling
            logger.info("Inizializzazione ConfigManager...");
            configManager = new ConfigManager(this);

            logger.info("Inizializzazione EconomyManager...");
            economyManager = new EconomyManager(this);

            logger.info("Inizializzazione RateLimiter...");
            rateLimiter = new RateLimiter(configManager);

            logger.info("Inizializzazione StatisticsManager...");
            statisticsManager = new StatisticsManager(this);

            logger.info("Inizializzazione DatabaseManager...");
            databaseManager = new DatabaseManager(this);

            // Verifica integrità database
            if (!databaseManager.checkDatabaseIntegrity()) {
                logger.warning("Controllo integrità database fallito - il database potrebbe essere corrotto");
            }

            logger.info("Inizializzazione CityManager...");
            cityManager = new CityManager(this, configManager);

            logger.info("Tutti i manager inizializzati correttamente");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore durante l'inizializzazione dei manager", e);
            throw new RuntimeException("Inizializzazione fallita", e);
        }
    }

    private void registerCommands() {
        try {
            // Registra i comandi con i nuovi tab completers
            logger.info("Registrazione comandi...");

            var cittaCommand = getCommand("citta");
            if (cittaCommand != null) {
                cittaCommand.setExecutor(new CityCommand(this, cityManager, economyManager,
                        rateLimiter, statisticsManager, databaseManager));
                cittaCommand.setTabCompleter(new CityTabCompleter());
            } else {
                throw new RuntimeException("Comando 'citta' non trovato in plugin.yml");
            }

            var adminCommand = getCommand("cittaadmin");
            if (adminCommand != null) {
                adminCommand.setExecutor(new AdminCommand(this, cityManager, statisticsManager));
                adminCommand.setTabCompleter(new AdminTabCompleter());
            } else {
                throw new RuntimeException("Comando 'cittaadmin' non trovato in plugin.yml");
            }

            logger.info("Comandi registrati correttamente");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore durante la registrazione dei comandi", e);
            throw new RuntimeException("Registrazione comandi fallita", e);
        }
    }

    private void scheduleTasks() {
        try {
            logger.info("Schedulazione task periodici ottimizzati...");

            // Task cache cleanup RIDOTTO - ogni 10 minuti invece di ogni ora
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    if (cityManager != null && !getServer().getScheduler().getPendingTasks().isEmpty()) {
                        cityManager.clearExpiredCache();
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Errore durante la pulizia cache", e);
                }
            }, 20L * 600L, 20L * 600L); // 10 minuti invece di 1 ora

            // Task statistiche OTTIMIZZATO - solo se dirty
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    if (statisticsManager != null) {
                        // Il nuovo StatisticsManager salva automaticamente solo se necessario
                        // Non serve più chiamata esplicita qui
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Errore durante verifica statistiche", e);
                }
            }, 20L * 600L, 20L * 600L); // 10 minuti

            // Task database cleanup RIDOTTO - ogni 6 ore invece di ogni giorno
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    if (databaseManager != null && configManager != null &&
                            configManager.isTeleportDayCooldownEnabled()) {

                        int daysToKeep = (configManager.getTeleportCooldownDays() * 2) + 30;
                        databaseManager.clearOldTeleports(daysToKeep);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Errore durante la pulizia database", e);
                }
            }, 20L * 21600L, 20L * 21600L); // 6 ore invece di 24

            // Task verifica integrità database RIDOTTO - ogni 3 giorni invece di ogni settimana
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    if (databaseManager != null && !databaseManager.checkDatabaseIntegrity()) {
                        logger.severe("Controllo integrità database fallito! Possibile corruzione dati.");
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Errore durante verifica integrità database", e);
                }
            }, 20L * 259200L, 20L * 259200L); // 3 giorni

            logger.info("Task ottimizzati schedulati correttamente");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore durante la schedulazione dei task", e);
            throw new RuntimeException("Schedulazione task fallita", e);
        }
    }

    private void logStartupInfo() {
        try {
            logger.info("=== LOCATECITIES STARTUP INFO ===");
            logger.info("Versione plugin: " + getDescription().getVersion());
            logger.info("Database offline contiene " + OfflineCityDatabase.getCityCount() + " città");

            if (economyManager != null && economyManager.isEconomyEnabled()) {
                logger.info("Economy abilitata - Costo ricerca: $" + economyManager.getSearchCost() +
                        ", Costo teleport: $" + economyManager.getTeleportCost());
            } else {
                logger.info("Economy disabilitata");
            }

            if (configManager != null && configManager.isTeleportDayCooldownEnabled()) {
                logger.info("Sistema cooldown giorni attivo - Ogni " + configManager.getTeleportCooldownDays() +
                        " giorni (cooldown globale per qualsiasi città)");
            } else {
                logger.info("Sistema cooldown giorni disattivo");
            }

            if (configManager != null && configManager.isRateLimitEnabled()) {
                logger.info("Rate limiting abilitato - Ricerca: " + configManager.getSearchCooldown() +
                        "s, Teleport: " + configManager.getTeleportCooldown() + "s");
            } else {
                logger.info("Rate limiting disabilitato");
            }

            if (databaseManager != null) {
                logger.info("Statistiche database: " + databaseManager.getDatabaseStats());
            }

            logger.info("Comandi disponibili:");
            logger.info("- /citta search <nome> - Cerca una città");
            logger.info("- /citta tp <nome> - Teletrasportati");
            logger.info("- /citta history - Cronologia teleport");
            logger.info("- /citta tutorial - Tutorial interattivo");
            logger.info("Tab completion abilitato per tutti i comandi!");
            logger.info("================================");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Errore durante il logging delle informazioni di startup", e);
        }
    }

    @Override
    public void onDisable() {
        logger.info("Disabilitazione LocateCities in corso...");

        try {
            // Cancella tutti i task schedulati PRIMA di tutto
            getServer().getScheduler().cancelTasks(this);

            // NUOVO: Shutdown ottimizzato dei manager
            if (cityManager != null) {
                cityManager.shutdown(); // Nuovo metodo per cleanup asincrono
            }

            if (statisticsManager != null) {
                statisticsManager.shutdown(); // Nuovo metodo per salvataggio batch
            }

            if (databaseManager != null) {
                databaseManager.close(); // Già ottimizzato
            }

            // Pulizia riferimenti
            cityManager = null;
            configManager = null;
            economyManager = null;
            rateLimiter = null;
            statisticsManager = null;
            databaseManager = null;

            pluginInitialized = false;

            logger.info("LocateCities plugin disabilitato correttamente!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore durante la disabilitazione del plugin", e);
        }
    }

    // Getter con controlli di sicurezza
    public ConfigManager getConfigManager() {
        if (configManager == null) {
            throw new IllegalStateException("ConfigManager non disponibile");
        }
        return configManager;
    }

    public EconomyManager getEconomyManager() {
        if (economyManager == null) {
            throw new IllegalStateException("EconomyManager non disponibile");
        }
        return economyManager;
    }

    public RateLimiter getRateLimiter() {
        if (rateLimiter == null) {
            throw new IllegalStateException("RateLimiter non disponibile");
        }
        return rateLimiter;
    }

    public StatisticsManager getStatisticsManager() {
        if (statisticsManager == null) {
            throw new IllegalStateException("StatisticsManager non disponibile");
        }
        return statisticsManager;
    }

    public CityManager getCityManager() {
        if (cityManager == null) {
            throw new IllegalStateException("CityManager non disponibile");
        }
        return cityManager;
    }

    public DatabaseManager getDatabaseManager() {
        if (databaseManager == null) {
            throw new IllegalStateException("DatabaseManager non disponibile");
        }
        return databaseManager;
    }

    /**
     * Verifica se il plugin è correttamente inizializzato
     */
    public boolean isInitialized() {
        return pluginInitialized;
    }

    /**
     * Ottiene informazioni di debug sul plugin
     */
    public String getDebugInfo() {
        if (!pluginInitialized) {
            return "Plugin non inizializzato";
        }

        StringBuilder info = new StringBuilder();
        info.append("=== DEBUG INFO ===\n");
        info.append("Plugin inizializzato: ").append(pluginInitialized).append("\n");
        info.append("Versione: ").append(getDescription().getVersion()).append("\n");

        try {
            if (configManager != null) {
                info.append(configManager.getConfigurationDebugInfo());
            }

            if (databaseManager != null) {
                info.append("Database: ").append(databaseManager.getDatabaseStats()).append("\n");
            }

            if (cityManager != null) {
                info.append("Cache size: ").append(cityManager.getCacheSize()).append("\n");
            }

            if (statisticsManager != null) {
                info.append("Ricerche totali: ").append(statisticsManager.getTotalSearches()).append("\n");
                info.append("Teleport totali: ").append(statisticsManager.getTotalTeleports()).append("\n");
            }

        } catch (Exception e) {
            info.append("Errore nel recupero debug info: ").append(e.getMessage()).append("\n");
        }

        return info.toString();
    }

    /**
     * Forza un reload sicuro del plugin
     */
    public boolean reloadPlugin() {
        try {
            logger.info("Reload plugin richiesto...");

            if (configManager != null) {
                configManager.reload();
            }

            if (rateLimiter != null) {
                rateLimiter.clearCooldowns();
            }

            logger.info("Reload completato con successo");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore durante il reload del plugin", e);
            return false;
        }
    }
}