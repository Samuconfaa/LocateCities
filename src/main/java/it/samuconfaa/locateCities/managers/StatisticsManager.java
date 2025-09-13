package it.samuconfaa.locateCities.managers;

import it.samuconfaa.locateCities.LocateCities;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class StatisticsManager {

    private final LocateCities plugin;
    private final File statsFile;

    // Statistiche ottimizzate con strutture thread-safe
    private final ConcurrentHashMap<String, AtomicInteger> citySearchCount;
    private final ConcurrentHashMap<String, AtomicInteger> playerSearchCount;
    private final ConcurrentHashMap<String, AtomicLong> lastApiRequest;

    // Contatori atomici per performance
    private final AtomicInteger totalSearches = new AtomicInteger(0);
    private final AtomicInteger totalTeleports = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger apiCalls = new AtomicInteger(0);

    // Batch saving per ridurre I/O
    private final ScheduledExecutorService saveExecutor;
    private volatile boolean pendingSave = false;
    private final AtomicLong lastSaveTime = new AtomicLong(System.currentTimeMillis());

    // Limiti per memory management
    private static final int MAX_CITY_ENTRIES = 1000;
    private static final int MAX_PLAYER_ENTRIES = 500;
    private static final long SAVE_INTERVAL_MS = 300_000L; // 5 minuti
    private static final long CLEANUP_INTERVAL_MS = 600_000L; // 10 minuti

    public StatisticsManager(LocateCities plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "statistics.yml");

        // Inizializza strutture ottimizzate
        this.citySearchCount = new ConcurrentHashMap<>(64, 0.75f, 4);
        this.playerSearchCount = new ConcurrentHashMap<>(32, 0.75f, 2);
        this.lastApiRequest = new ConcurrentHashMap<>(64, 0.75f, 4);

        // Thread executor per salvataggi asincroni
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LocateCities-Stats");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        loadStatistics();
        scheduleOptimizedTasks();
    }

    private void loadStatistics() {
        if (!statsFile.exists()) {
            return;
        }

        saveExecutor.execute(() -> {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);

                // Carica statistiche generali
                totalSearches.set(config.getInt("general.total_searches", 0));
                totalTeleports.set(config.getInt("general.total_teleports", 0));
                cacheHits.set(config.getInt("general.cache_hits", 0));
                apiCalls.set(config.getInt("general.api_calls", 0));

                // Carica con limite per prevenire memory issues
                loadLimitedSection(config, "cities", citySearchCount, MAX_CITY_ENTRIES);
                loadLimitedSection(config, "players", playerSearchCount, MAX_PLAYER_ENTRIES);

                plugin.getLogger().info("Statistiche caricate: " + totalSearches.get() + " ricerche totali");

            } catch (Exception e) {
                plugin.getLogger().warning("Errore caricamento statistiche: " + e.getMessage());
            }
        });
    }

    private void loadLimitedSection(FileConfiguration config, String section,
                                    ConcurrentHashMap<String, AtomicInteger> map, int maxEntries) {
        if (!config.isConfigurationSection(section)) return;

        Set<String> keys = config.getConfigurationSection(section).getKeys(false);

        // Carica solo le top N entries per limitare memoria
        keys.stream()
                .sorted((k1, k2) -> Integer.compare(
                        config.getInt(section + "." + k2, 0),
                        config.getInt(section + "." + k1, 0)
                ))
                .limit(maxEntries)
                .forEach(key -> {
                    int count = config.getInt(section + "." + key, 0);
                    if (count > 0) {
                        map.put(key, new AtomicInteger(count));
                    }
                });
    }

    private void scheduleOptimizedTasks() {
        // Salvataggio periodico solo se ci sono modifiche
        saveExecutor.scheduleWithFixedDelay(() -> {
            if (pendingSave) {
                saveStatistics();
                pendingSave = false;
            }
        }, SAVE_INTERVAL_MS, SAVE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Cleanup periodico per limitare memoria
        saveExecutor.scheduleWithFixedDelay(this::cleanupOldEntries,
                CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void cleanupOldEntries() {
        // Rimuovi entry con conteggi bassi dalle statistiche città
        if (citySearchCount.size() > MAX_CITY_ENTRIES) {
            cleanupLowCountEntries(citySearchCount, MAX_CITY_ENTRIES * 3/4, 2);
        }

        // Rimuovi entry con conteggi bassi dalle statistiche giocatori
        if (playerSearchCount.size() > MAX_PLAYER_ENTRIES) {
            cleanupLowCountEntries(playerSearchCount, MAX_PLAYER_ENTRIES * 3/4, 1);
        }

        // Cleanup vecchie richieste API (più di 24 ore)
        long cutoff = System.currentTimeMillis() - 86400000L; // 24 ore
        lastApiRequest.entrySet().removeIf(entry -> entry.getValue().get() < cutoff);

        plugin.getLogger().fine("Cleanup statistiche completato");
    }

    private void cleanupLowCountEntries(ConcurrentHashMap<String, AtomicInteger> map,
                                        int targetSize, int minCount) {
        map.entrySet().removeIf(entry ->
                map.size() > targetSize && entry.getValue().get() <= minCount);
    }

    // Metodi per registrare eventi - OTTIMIZZATI
    public void recordSearch(String cityName, Player player, boolean fromCache) {
        totalSearches.incrementAndGet();

        String normalizedCity = cityName.toLowerCase().trim();

        // Usa computeIfAbsent per thread safety
        citySearchCount.computeIfAbsent(normalizedCity, k -> new AtomicInteger(0)).incrementAndGet();

        if (player != null) {
            String playerName = player.getName();
            playerSearchCount.computeIfAbsent(playerName, k -> new AtomicInteger(0)).incrementAndGet();
        }

        if (fromCache) {
            cacheHits.incrementAndGet();
        } else {
            apiCalls.incrementAndGet();
            lastApiRequest.put(normalizedCity, new AtomicLong(System.currentTimeMillis()));
        }

        pendingSave = true;
    }

    public void recordTeleport() {
        totalTeleports.incrementAndGet();
        pendingSave = true;
    }

    // Metodi getter ottimizzati
    public int getTotalSearches() { return totalSearches.get(); }
    public int getTotalTeleports() { return totalTeleports.get(); }
    public int getCacheHits() { return cacheHits.get(); }
    public int getApiCalls() { return apiCalls.get(); }

    public double getCacheHitRate() {
        int hits = cacheHits.get();
        int total = totalSearches.get();
        return total > 0 ? (double) hits / total * 100 : 0.0;
    }

    public List<Map.Entry<String, Integer>> getTopCities(int limit) {
        return citySearchCount.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().get()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(Math.min(limit, citySearchCount.size()))
                .collect(Collectors.toList());
    }

    public List<Map.Entry<String, Integer>> getTopPlayers(int limit) {
        return playerSearchCount.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().get()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(Math.min(limit, playerSearchCount.size()))
                .collect(Collectors.toList());
    }

    public int getCitySearchCount(String cityName) {
        AtomicInteger count = citySearchCount.get(cityName.toLowerCase());
        return count != null ? count.get() : 0;
    }

    public int getPlayerSearchCount(String playerName) {
        AtomicInteger count = playerSearchCount.get(playerName);
        return count != null ? count.get() : 0;
    }

    // Algoritmo di similarità ottimizzato
    public List<String> getNearCities(String cityName, int limit) {
        String normalizedName = cityName.toLowerCase().trim();

        return citySearchCount.entrySet().parallelStream()
                .filter(entry -> !entry.getKey().equals(normalizedName))
                .filter(entry -> {
                    String city = entry.getKey();
                    // Algoritmo di similarità semplificato per performance
                    return city.contains(normalizedName) ||
                            normalizedName.contains(city) ||
                            calculateSimilarity(city, normalizedName) > 0.5;
                })
                .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        return 1.0 - (double) levenshteinDistance(s1, s2) / maxLen;
    }

    private int levenshteinDistance(String s1, String s2) {
        // Implementazione ottimizzata di Levenshtein
        if (s1.length() > s2.length()) {
            String temp = s1;
            s1 = s2;
            s2 = temp;
        }

        int[] previousRow = new int[s1.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            previousRow[i] = i;
        }

        for (int i = 1; i <= s2.length(); i++) {
            int[] currentRow = new int[s1.length() + 1];
            currentRow[0] = i;

            for (int j = 1; j <= s1.length(); j++) {
                int cost = s1.charAt(j - 1) == s2.charAt(i - 1) ? 0 : 1;
                currentRow[j] = Math.min(
                        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                        previousRow[j - 1] + cost
                );
            }

            previousRow = currentRow;
        }

        return previousRow[s1.length()];
    }

    public void saveStatistics() {
        // Skip se salvato di recente
        long now = System.currentTimeMillis();
        if (now - lastSaveTime.get() < 10000) { // 10 secondi
            return;
        }

        saveExecutor.execute(() -> {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }

                FileConfiguration config = new YamlConfiguration();

                // Salva statistiche generali
                config.set("general.total_searches", totalSearches.get());
                config.set("general.total_teleports", totalTeleports.get());
                config.set("general.cache_hits", cacheHits.get());
                config.set("general.api_calls", apiCalls.get());
                config.set("general.last_update", now);

                // Salva solo top entries per limitare file size
                saveTopEntries(config, "cities", citySearchCount, 100);
                saveTopEntries(config, "players", playerSearchCount, 50);

                config.save(statsFile);
                lastSaveTime.set(now);

            } catch (IOException e) {
                plugin.getLogger().warning("Errore salvataggio statistiche: " + e.getMessage());
            }
        });
    }

    private void saveTopEntries(FileConfiguration config, String section,
                                ConcurrentHashMap<String, AtomicInteger> map, int limit) {
        map.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get()))
                .limit(limit)
                .forEach(entry -> {
                    config.set(section + "." + entry.getKey(), entry.getValue().get());
                });
    }

    public void clearStatistics() {
        citySearchCount.clear();
        playerSearchCount.clear();
        lastApiRequest.clear();
        totalSearches.set(0);
        totalTeleports.set(0);
        cacheHits.set(0);
        apiCalls.set(0);
        saveStatistics();
    }

    // Shutdown ottimizzato
    public void shutdown() {
        // Forza salvataggio finale
        saveStatistics();

        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}