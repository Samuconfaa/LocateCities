package it.samuconfaa.locateCities;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Arrays;

public class LocateCities extends JavaPlugin {

    private CityManager cityManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        // Salva la configurazione di default se non esiste
        saveDefaultConfig();

        // Inizializza i manager
        configManager = new ConfigManager(this);
        cityManager = new CityManager(this, configManager);

        // Registra i comandi
        getCommand("citta").setExecutor(new CityCommand(cityManager));
        getCommand("citta").setTabCompleter(new CityTabCompleter());
        getCommand("cittaadmin").setExecutor(new AdminCommand(this, cityManager));

        // Task per pulire la cache scaduta ogni ora
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            cityManager.clearExpiredCache();
        }, 20L * 3600L, 20L * 3600L); // 1 ora = 3600 secondi = 72000 tick

        getLogger().info("LocateCities plugin abilitato!");
        getLogger().info("Database offline contiene " + OfflineCityDatabase.getCityCount() + " città");
    }

    @Override
    public void onDisable() {
        if (cityManager != null) {
            cityManager.saveCache();
        }
        getLogger().info("LocateCities plugin disabilitato!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}