package it.samuconfaa.locateCities.managers;

import it.samuconfaa.locateCities.LocateCities;
import it.samuconfaa.locateCities.database.OfflineCityDatabase;
import it.samuconfaa.locateCities.data.CityData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CityManager {

    private final LocateCities plugin;
    public final ConfigManager configManager;
    private final GeocodingService geocodingService;
    private final Map<String, CityData> cache;
    private final File cacheFile;
    private boolean cacheDirty = false;

    // Cache con limite di dimensione
    private static final int MAX_CACHE_ENTRIES = 1000;

    public CityManager(LocateCities plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.geocodingService = new GeocodingService(configManager.getApiTimeout());
        this.cache = new LinkedHashMap<String, CityData>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CityData> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        };
        this.cacheFile = new File(plugin.getDataFolder(), "city_cache.yml");

        loadCache();

        // Salva cache periodicamente (ogni 5 minuti) se dirty
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (cacheDirty) {
                saveCache();
                cacheDirty = false;
            }
        }, 20L * 300L, 20L * 300L); // 5 minuti
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
            cacheDirty = true;
            return CompletableFuture.completedFuture(offline);
        }

        // Cerca online
        return geocodingService.searchCity(cityName)
                .thenApply(cityData -> {
                    // Salva in cache
                    cache.put(normalizedName, cityData);
                    cacheDirty = true;
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

        // Applica inversioni se configurate
        if (configManager.isInvertX()) {
            x = -x;
        }
        if (configManager.isInvertZ()) {
            z = -z;
        }

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
        int sizeBefore = cache.size();
        cache.entrySet().removeIf(entry ->
                entry.getValue().isExpired(configManager.getCacheDurationHours()));

        int sizeAfter = cache.size();
        int removed = sizeBefore - sizeAfter;

        if (removed > 0) {
            cacheDirty = true;
            plugin.getLogger().info("Rimosse " + removed + " entry scadute dalla cache");
        }
    }

    public int getCacheSize() {
        return cache.size();
    }

    public void clearAllCache() {
        cache.clear();
        cacheDirty = true;
    }
}