package it.samuconfaa.locateCities;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final LocateCities plugin;
    private FileConfiguration config;

    public ConfigManager(LocateCities plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        setupDefaults();
    }

    private void setupDefaults() {
        config.addDefault("lat_origin", 0.0);
        config.addDefault("lon_origin", 0.0);
        config.addDefault("scale", 100.0);
        config.addDefault("y_default", 64);
        config.addDefault("enable_teleport", true);
        config.addDefault("use_terrain_height", true);
        config.addDefault("api_timeout", 5000);
        config.addDefault("cache_duration_hours", 24);
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public double getLatOrigin() {
        return config.getDouble("lat_origin");
    }

    public double getLonOrigin() {
        return config.getDouble("lon_origin");
    }

    public double getScale() {
        return config.getDouble("scale");
    }

    public int getDefaultY() {
        return config.getInt("y_default");
    }

    public boolean isTeleportEnabled() {
        return config.getBoolean("enable_teleport");
    }

    public boolean useTerrainHeight() {
        return config.getBoolean("use_terrain_height");
    }

    public int getApiTimeout() {
        return config.getInt("api_timeout");
    }

    public int getCacheDurationHours() {
        return config.getInt("cache_duration_hours");
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
}