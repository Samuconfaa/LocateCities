package it.samuconfaa.locateCities.managers;

import it.samuconfaa.locateCities.LocateCities;
import it.samuconfaa.locateCities.database.OfflineCityDatabase;
import it.samuconfaa.locateCities.data.CityData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CityManager {

    private final LocateCities plugin;
    public final ConfigManager configManager;
    private final GeocodingService geocodingService;

    // Cache ottimizzata con LRU e TTL
    private final ConcurrentHashMap<String, CachedCity> cache;
    private final int maxCacheSize;
    private final long cacheLifetime;

    // Thread pool dedicato per operazioni I/O
    private final ScheduledExecutorService ioExecutor;

    // Batch operations per performance
    private final ConcurrentLinkedQueue<CacheEntry> pendingSaves;
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    // Metriche performance
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger apiCalls = new AtomicInteger(0);

    private final File cacheFile;
    private volatile boolean cacheDirty = false;

    private static final int DEFAULT_MAX_CACHE_SIZE = 500; // Ridotto da 1000
    private static final long DEFAULT_CACHE_LIFETIME = 86400000L; // 24 ore in ms
    private static final int CLEANUP_BATCH_SIZE = 50;

    public CityManager(LocateCities plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.geocodingService = new GeocodingService(configManager.getApiTimeout());

        this.maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
        this.cacheLifetime = DEFAULT_CACHE_LIFETIME;
        this.cache = new ConcurrentHashMap<>(maxCacheSize / 4, 0.75f, 4);
        this.pendingSaves = new ConcurrentLinkedQueue<>();

        // Thread pool ottimizzato per I/O
        this.ioExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LocateCities-IO");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        this.cacheFile = new File(plugin.getDataFolder(), "city_cache.yml");

        loadCache();
        scheduleOptimizedTasks();
    }

    public CompletableFuture<CityData> findCity(String cityName) {
        String normalizedName = cityName.toLowerCase().trim();

        // 1. Cache check veloce (O(1))
        CachedCity cached = cache.get(normalizedName);
        if (cached != null && !cached.isExpired()) {
            cacheHits.incrementAndGet();
            cached.updateLastAccess(); // LRU
            return CompletableFuture.completedFuture(cached.cityData);
        }

        cacheMisses.incrementAndGet();

        // 2. Database offline check
        CityData offline = OfflineCityDatabase.findCity(normalizedName);
        if (offline != null) {
            putInCache(normalizedName, offline);
            return CompletableFuture.completedFuture(offline);
        }

        // 3. API call asincrona con deduplicazione
        return CompletableFuture.supplyAsync(() -> {
            try {
                apiCalls.incrementAndGet();

                // Previeni richieste duplicate simultanee
                String loadingKey = normalizedName + "_loading";
                CachedCity inProgress = cache.putIfAbsent(loadingKey,
                        new CachedCity(null, System.currentTimeMillis() + 30000));

                if (inProgress != null && !inProgress.isExpired()) {
                    // Attendi brevemente e ricontrolla cache
                    Thread.sleep(100);
                    CachedCity completed = cache.get(normalizedName);
                    if (completed != null && !completed.isExpired()) {
                        cache.remove(loadingKey);
                        return completed.cityData;
                    }
                }

                CityData result = geocodingService.searchCity(normalizedName)
                        .get(configManager.getApiTimeout() + 5000, TimeUnit.MILLISECONDS);

                cache.remove(loadingKey);
                putInCache(normalizedName, result);
                return result;

            } catch (Exception e) {
                cache.remove(normalizedName + "_loading");
                throw new CompletionException("Città non trovata: " + cityName, e);
            }
        }, ioExecutor);
    }

    private void putInCache(String key, CityData cityData) {
        // Pulizia proattiva se cache troppo piena
        if (cache.size() >= maxCacheSize) {
            cleanupCacheLRU();
        }

        CachedCity cached = new CachedCity(cityData, System.currentTimeMillis() + cacheLifetime);
        cache.put(key, cached);
        cacheDirty = true;

        // Batch save asincrono
        pendingSaves.offer(new CacheEntry(key, cityData));
        scheduleBatchSave();
    }

    private void cleanupCacheLRU() {
        if (cache.isEmpty()) return;

        // Prima rimuovi elementi scaduti
        cache.entrySet().removeIf(entry ->
                entry.getKey().endsWith("_loading") || entry.getValue().isExpired());

        // Se ancora piena, rimuovi elementi LRU
        if (cache.size() >= maxCacheSize) {
            int toRemove = Math.max(maxCacheSize / 10, 5);

            cache.entrySet().stream()
                    .filter(e -> !e.getKey().endsWith("_loading"))
                    .sorted((e1, e2) -> Long.compare(e1.getValue().lastAccess, e2.getValue().lastAccess))
                    .limit(toRemove)
                    .map(entry -> entry.getKey())
                    .forEach(cache::remove);
        }
    }

    private void scheduleBatchSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            // Salvataggio batch ogni 30 secondi
            ioExecutor.schedule(() -> {
                try {
                    savePendingEntries();
                } finally {
                    saveScheduled.set(false);
                }
            }, 30, TimeUnit.SECONDS);
        }
    }

    private void savePendingEntries() {
        if (pendingSaves.isEmpty()) return;

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            FileConfiguration cacheConfig = cacheFile.exists() ?
                    YamlConfiguration.loadConfiguration(cacheFile) : new YamlConfiguration();

            int saved = 0;
            CacheEntry entry;

            // Processa in batch per performance
            while ((entry = pendingSaves.poll()) != null && saved < CLEANUP_BATCH_SIZE) {
                CityData cityData = entry.cityData;
                String key = entry.key;

                cacheConfig.set(key + ".name", cityData.getName());
                cacheConfig.set(key + ".latitude", cityData.getLatitude());
                cacheConfig.set(key + ".longitude", cityData.getLongitude());
                cacheConfig.set(key + ".timestamp", cityData.getTimestamp());
                saved++;
            }

            if (saved > 0) {
                cacheConfig.save(cacheFile);
                cacheDirty = false;
                plugin.getLogger().fine("Salvate " + saved + " city cache entries");
            }

        } catch (IOException e) {
            plugin.getLogger().warning("Errore salvataggio cache batch: " + e.getMessage());
        }
    }

    private void loadCache() {
        if (!cacheFile.exists()) return;

        ioExecutor.execute(() -> {
            try {
                FileConfiguration cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);
                int loaded = 0;

                for (String key : cacheConfig.getKeys(false)) {
                    if (loaded >= maxCacheSize) break;

                    try {
                        String name = cacheConfig.getString(key + ".name");
                        double lat = cacheConfig.getDouble(key + ".latitude");
                        double lon = cacheConfig.getDouble(key + ".longitude");
                        long timestamp = cacheConfig.getLong(key + ".timestamp");

                        // Solo entry non scadute
                        if (System.currentTimeMillis() - timestamp < cacheLifetime) {
                            CityData cityData = new CityData(name, lat, lon, timestamp);
                            CachedCity cached = new CachedCity(cityData, timestamp + cacheLifetime);
                            cache.put(key, cached);
                            loaded++;
                        }
                    } catch (Exception e) {
                        // Skip entry corrotta
                    }
                }

                plugin.getLogger().info("Caricate " + loaded + " città dalla cache");

            } catch (Exception e) {
                plugin.getLogger().warning("Errore caricamento cache: " + e.getMessage());
            }
        });
    }

    private void scheduleOptimizedTasks() {
        // Cleanup cache ogni 10 minuti (invece di ogni ora)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::cleanupExpiredCache, 20L * 600L, 20L * 600L);

        // Salvataggio cache solo se dirty ogni 5 minuti
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (cacheDirty) {
                scheduleBatchSave();
            }
        }, 20L * 300L, 20L * 300L);
    }

    public void clearExpiredCache() {
        cleanupExpiredCache();
    }

    private void cleanupExpiredCache() {
        int before = cache.size();
        cache.entrySet().removeIf(entry ->
                entry.getKey().endsWith("_loading") || entry.getValue().isExpired());

        int removed = before - cache.size();
        if (removed > 0) {
            cacheDirty = true;
            plugin.getLogger().fine("Rimossi " + removed + " elementi scaduti dalla cache");
        }
    }

    public Location getMinecraftLocation(CityData cityData, Player player) {
        // Ottieni il mondo di destinazione configurato
        String targetWorldName = configManager.getTargetWorldName();
        World targetWorld = plugin.getServer().getWorld(targetWorldName);

        // Se il mondo target non esiste, prova il mondo principale
        if (targetWorld == null) {
            plugin.getLogger().warning("Mondo target '" + targetWorldName + "' non trovato, usando mondo principale");
            targetWorld = plugin.getServer().getWorlds().get(0); // Mondo principale

            // Se ancora null, usa il mondo del giocatore come fallback
            if (targetWorld == null) {
                plugin.getLogger().severe("Impossibile trovare un mondo valido!");
                targetWorld = player.getWorld();
            }
        }

        return getMinecraftLocationInWorld(cityData, targetWorld);
    }

    public Location getMinecraftLocationInWorld(CityData cityData, World world) {
        CityData.MinecraftCoordinates coords = cityData.toMinecraftCoordinates(configManager);

        int x = coords.getX();
        int z = coords.getZ();
        int y = coords.getY();

        // Ottimizzazioni coordinate
        if (configManager.isInvertX()) x = -x;
        if (configManager.isInvertZ()) z = -z;

        if (configManager.useTerrainHeight()) {
            y = world.getHighestBlockYAt(x, z) + 1;
        }

        return new Location(world, x, y, z);
    }

    @Deprecated
    public Location getMinecraftLocation(CityData cityData, World world) {
        return getMinecraftLocationInWorld(cityData, world);
    }

    // API esistenti mantenute
    public int getCacheSize() {
        return cache.size();
    }

    public double getCacheHitRate() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        return hits + misses > 0 ? (double) hits / (hits + misses) * 100 : 0.0;
    }

    public void clearAllCache() {
        cache.clear();
        cacheDirty = true;
    }

    public void saveCache() {
        if (cacheDirty) {
            scheduleBatchSave();
        }
    }

    // Shutdown cleanup
    public void shutdown() {
        savePendingEntries();
        saveCache();

        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Classi helper interne
    private static class CachedCity {
        final CityData cityData;
        final long expirationTime;
        volatile long lastAccess;

        CachedCity(CityData cityData, long expirationTime) {
            this.cityData = cityData;
            this.expirationTime = expirationTime;
            this.lastAccess = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        void updateLastAccess() {
            this.lastAccess = System.currentTimeMillis();
        }
    }

    private static class CacheEntry {
        final String key;
        final CityData cityData;

        CacheEntry(String key, CityData cityData) {
            this.key = key;
            this.cityData = cityData;
        }
    }
}