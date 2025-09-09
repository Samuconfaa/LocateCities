package it.samuconfaa.locateCities;

import org.bukkit.ChatColor;
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
        // Coordinate defaults
        config.addDefault("lat_origin", 0.0);
        config.addDefault("lon_origin", 0.0);
        config.addDefault("scale", 100.0);
        config.addDefault("y_default", 64);

        // Feature defaults
        config.addDefault("enable_teleport", true);
        config.addDefault("use_terrain_height", true);
        config.addDefault("invert_x", false);
        config.addDefault("invert_z", false);

        // API defaults
        config.addDefault("api_timeout", 5000);
        config.addDefault("cache_duration_hours", 24);

        // Economy defaults
        config.addDefault("economy.enable", true);
        config.addDefault("economy.search_cost", 10.0);
        config.addDefault("economy.teleport_cost", 50.0);
        config.addDefault("economy.free_distance", 1000);

        // Rate limiting defaults
        config.addDefault("rate_limit.enabled", true);
        config.addDefault("rate_limit.search_cooldown", 3);
        config.addDefault("rate_limit.teleport_cooldown", 10);

        // Message defaults
        config.addDefault("messages.searching", "&e🔍 Ricerca di &f{city} &ein corso...");
        config.addDefault("messages.found", "&a📍 &f{city} &asi trova alle coordinate &bX:{x} Z:{z}");
        config.addDefault("messages.teleported", "&a✈ Teletrasportato a &f{city}&a!");
        config.addDefault("messages.not_found", "&c❌ Città non trovata: &f{city}");
        config.addDefault("messages.teleport_disabled", "&c❌ Il teletrasporto è disabilitato!");
        config.addDefault("messages.no_permission", "&c❌ Non hai il permesso per usare questo comando!");
        config.addDefault("messages.no_permission_teleport", "&c❌ Non hai il permesso per teletrasportarti!");
        config.addDefault("messages.rate_limited_search", "&c❌ Devi aspettare &f{seconds} &csecondi prima di cercare un'altra città!");
        config.addDefault("messages.rate_limited_teleport", "&c❌ Devi aspettare &f{seconds} &csecondi prima di teletrasportarti di nuovo!");
        config.addDefault("messages.insufficient_funds", "&c❌ Non hai abbastanza soldi! Serve: &f${cost}");
        config.addDefault("messages.economy_charged", "&a✅ Addebitati &f${cost} &aper la ricerca");
        config.addDefault("messages.teleport_charged", "&a✅ Addebitati &f${cost} &aper il teletrasporto");
        config.addDefault("messages.free_teleport", "&a✅ Teletrasporto gratuito (distanza: &f{distance} &ablocchi)");
        config.addDefault("messages.config_reloaded", "&a✅ Configurazione ricaricata!");
        config.addDefault("messages.cache_cleared", "&a✅ Cache pulita!");
        config.addDefault("messages.origin_set", "&a✅ Origine impostata a: &f{lat}, {lon}");
        config.addDefault("messages.scale_set", "&a✅ Scala impostata a: &f{scale}");
        config.addDefault("messages.invalid_coordinates", "&c❌ Coordinate non valide!");
        config.addDefault("messages.invalid_scale", "&c❌ Scala non valida!");
        config.addDefault("messages.error_general", "&c❌ Errore: &f{error}");

        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    // Coordinate methods
    public double getLatOrigin() { return config.getDouble("lat_origin"); }
    public double getLonOrigin() { return config.getDouble("lon_origin"); }
    public double getScale() { return config.getDouble("scale"); }
    public int getDefaultY() { return config.getInt("y_default"); }
    public boolean isInvertX() { return config.getBoolean("invert_x"); }
    public boolean isInvertZ() { return config.getBoolean("invert_z"); }

    // Feature methods
    public boolean isTeleportEnabled() { return config.getBoolean("enable_teleport"); }
    public boolean useTerrainHeight() { return config.getBoolean("use_terrain_height"); }

    // API methods
    public int getApiTimeout() { return config.getInt("api_timeout"); }
    public int getCacheDurationHours() { return config.getInt("cache_duration_hours"); }

    // Economy methods
    public boolean isEconomyEnabled() { return config.getBoolean("economy.enable"); }
    public double getSearchCost() { return config.getDouble("economy.search_cost"); }
    public double getTeleportCost() { return config.getDouble("economy.teleport_cost"); }
    public int getFreeDistance() { return config.getInt("economy.free_distance"); }

    // Rate limiting methods
    public boolean isRateLimitEnabled() { return config.getBoolean("rate_limit.enabled"); }
    public int getSearchCooldown() { return config.getInt("rate_limit.search_cooldown"); }
    public int getTeleportCooldown() { return config.getInt("rate_limit.teleport_cooldown"); }

    // Message methods
    public String getMessage(String key, String... placeholders) {
        String message = config.getString("messages." + key, "&cMessaggio non trovato: " + key);

        // Sostituisci placeholder
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Validation methods
    public boolean validateCoordinates(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    public boolean validateScale(double scale) {
        return scale > 0 && scale <= 1000000; // Limite ragionevole
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
}