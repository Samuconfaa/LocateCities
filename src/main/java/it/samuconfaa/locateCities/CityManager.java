package it.samuconfaa.locateCities;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CityManager {

    private final LocateCities plugin;
    final ConfigManager configManager;
    private final GeocodingService geocodingService;
    private final Map<String, CityData> cache;
    private final File cacheFile;

    public CityManager(LocateCities plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.geocodingService = new GeocodingService(configManager.getApiTimeout());
        this.cache = new HashMap<>();
        this.cacheFile = new File(plugin.getDataFolder(), "city_cache.yml");

        loadCache();
    }

    public CompletableFuture<CityData> findCity(String cityName) {
        String normalizedName = cityName.toLowerCase().trim();

        // Controlla cache
        CityData cached = cache.get(normalizedName);
        if (cached != null && !cached.isExpired(configManager.getCacheDurationHours())) {
            return CompletableFuture.completedFuture(cached);
        }

        // Controlla database offline
        CityData offline = OfflineCityDatabase.findCity(cityName);
        if (offline != null) {
            // Salva in cache
            cache.put(normalizedName, offline);
            saveCache();
            return CompletableFuture.completedFuture(offline);
        }

        // Cerca online
        return geocodingService.searchCity(cityName)
                .thenApply(cityData -> {
                    // Salva in cache
                    cache.put(normalizedName, cityData);
                    saveCache();
                    return cityData;
                })
                .exceptionally(throwable -> {
                    // Se la ricerca online fallisce, restituisce null
                    throw new RuntimeException("Città non trovata: " + cityName +
                            ". Errore API: " + throwable.getMessage());
                });
    }

    public Location getMinecraftLocation(CityData cityData, World world) {
        CityData.MinecraftCoordinates coords = cityData.toMinecraftCoordinates(configManager);

        int x = coords.getX();
        int z = coords.getZ();
        int y = coords.getY();

        // Se abilitato, usa l'altezza del terreno
        if (configManager.useTerrainHeight()) {
            y = world.getHighestBlockYAt(x, z) + 1; // +1 per evitare di spawnare nel blocco
        }

        return new Location(world, x, y, z);
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }

        try {
            FileConfiguration cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);

            for (String key : cacheConfig.getKeys(false)) {
                String name = cacheConfig.getString(key + ".name");
                double lat = cacheConfig.getDouble(key + ".latitude");
                double lon = cacheConfig.getDouble(key + ".longitude");
                long timestamp = cacheConfig.getLong(key + ".timestamp");

                CityData cityData = new CityData(name, lat, lon, timestamp);
                cache.put(key, cityData);
            }

            plugin.getLogger().info("Caricate " + cache.size() + " città dalla cache");

        } catch (Exception e) {
            plugin.getLogger().warning("Errore nel caricamento della cache: " + e.getMessage());
        }
    }

    public void saveCache() {
        try {
            // Crea la directory se non esiste
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            FileConfiguration cacheConfig = new YamlConfiguration();

            for (Map.Entry<String, CityData> entry : cache.entrySet()) {
                String key = entry.getKey();
                CityData cityData = entry.getValue();

                cacheConfig.set(key + ".name", cityData.getName());
                cacheConfig.set(key + ".latitude", cityData.getLatitude());
                cacheConfig.set(key + ".longitude", cityData.getLongitude());
                cacheConfig.set(key + ".timestamp", cityData.getTimestamp());
            }

            cacheConfig.save(cacheFile);

        } catch (IOException e) {
            plugin.getLogger().warning("Errore nel salvataggio della cache: " + e.getMessage());
        }
    }

    public void clearExpiredCache() {
        cache.entrySet().removeIf(entry ->
                entry.getValue().isExpired(configManager.getCacheDurationHours()));
        saveCache();
    }
}