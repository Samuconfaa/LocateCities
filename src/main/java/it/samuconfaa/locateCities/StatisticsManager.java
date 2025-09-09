package it.samuconfaa.locateCities;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class StatisticsManager {

    private final LocateCities plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;

    // Statistiche in memoria
    private final Map<String, Integer> citySearchCount;
    private final Map<String, Integer> playerSearchCount;
    private final Map<String, Long> lastApiRequest;
    private int totalSearches;
    private int totalTeleports;
    private int cacheHits;
    private int apiCalls;

    public StatisticsManager(LocateCities plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "statistics.yml");
        this.citySearchCount = new HashMap<>();
        this.playerSearchCount = new HashMap<>();
        this.lastApiRequest = new HashMap<>();

        loadStatistics();
    }

    private void loadStatistics() {
        if (!statsFile.exists()) {
            statsConfig = new YamlConfiguration();
            saveStatistics();
            return;
        }

        try {
            statsConfig = YamlConfiguration.loadConfiguration(statsFile);

            // Carica statistiche generali
            totalSearches = statsConfig.getInt("general.total_searches", 0);
            totalTeleports = statsConfig.getInt("general.total_teleports", 0);
            cacheHits = statsConfig.getInt("general.cache_hits", 0);
            apiCalls = statsConfig.getInt("general.api_calls", 0);

            // Carica statistiche città
            if (statsConfig.isConfigurationSection("cities")) {
                for (String city : statsConfig.getConfigurationSection("cities").getKeys(false)) {
                    citySearchCount.put(city, statsConfig.getInt("cities." + city));
                }
            }

            // Carica statistiche giocatori
            if (statsConfig.isConfigurationSection("players")) {
                for (String player : statsConfig.getConfigurationSection("players").getKeys(false)) {
                    playerSearchCount.put(player, statsConfig.getInt("players." + player));
                }
            }

            plugin.getLogger().info("Caricate statistiche: " + totalSearches + " ricerche totali");

        } catch (Exception e) {
            plugin.getLogger().warning("Errore nel caricamento delle statistiche: " + e.getMessage());
            statsConfig = new YamlConfiguration();
        }
    }

    public void saveStatistics() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Salva statistiche generali
            statsConfig.set("general.total_searches", totalSearches);
            statsConfig.set("general.total_teleports", totalTeleports);
            statsConfig.set("general.cache_hits", cacheHits);
            statsConfig.set("general.api_calls", apiCalls);
            statsConfig.set("general.last_update", System.currentTimeMillis());

            // Salva statistiche città
            for (Map.Entry<String, Integer> entry : citySearchCount.entrySet()) {
                statsConfig.set("cities." + entry.getKey(), entry.getValue());
            }

            // Salva statistiche giocatori
            for (Map.Entry<String, Integer> entry : playerSearchCount.entrySet()) {
                statsConfig.set("players." + entry.getKey(), entry.getValue());
            }

            statsConfig.save(statsFile);

        } catch (IOException e) {
            plugin.getLogger().warning("Errore nel salvataggio delle statistiche: " + e.getMessage());
        }
    }

    // Metodi per registrare eventi
    public void recordSearch(String cityName, Player player, boolean fromCache) {
        totalSearches++;

        String normalizedCity = cityName.toLowerCase();
        citySearchCount.merge(normalizedCity, 1, Integer::sum);

        if (player != null) {
            playerSearchCount.merge(player.getName(), 1, Integer::sum);
        }

        if (fromCache) {
            cacheHits++;
        } else {
            apiCalls++;
            lastApiRequest.put(normalizedCity, System.currentTimeMillis());
        }
    }

    public void recordTeleport() {
        totalTeleports++;
    }

    // Metodi per ottenere statistiche
    public int getTotalSearches() { return totalSearches; }
    public int getTotalTeleports() { return totalTeleports; }
    public int getCacheHits() { return cacheHits; }
    public int getApiCalls() { return apiCalls; }

    public double getCacheHitRate() {
        if (totalSearches == 0) return 0.0;
        return (double) cacheHits / totalSearches * 100;
    }

    public List<Map.Entry<String, Integer>> getTopCities(int limit) {
        return citySearchCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Map.Entry<String, Integer>> getTopPlayers(int limit) {
        return playerSearchCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public int getCitySearchCount(String cityName) {
        return citySearchCount.getOrDefault(cityName.toLowerCase(), 0);
    }

    public int getPlayerSearchCount(String playerName) {
        return playerSearchCount.getOrDefault(playerName, 0);
    }

    // Cerca città vicine (entro un certo raggio di ricerche)
    public List<String> getNearCities(String cityName, int limit) {
        String normalizedName = cityName.toLowerCase();

        // Semplice algoritmo di similarità basato su substring
        return citySearchCount.keySet().stream()
                .filter(city -> !city.equals(normalizedName))
                .filter(city -> city.contains(normalizedName) || normalizedName.contains(city))
                .sorted((c1, c2) -> Integer.compare(
                        citySearchCount.get(c2),
                        citySearchCount.get(c1)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void clearStatistics() {
        citySearchCount.clear();
        playerSearchCount.clear();
        lastApiRequest.clear();
        totalSearches = 0;
        totalTeleports = 0;
        cacheHits = 0;
        apiCalls = 0;
        saveStatistics();
    }
}