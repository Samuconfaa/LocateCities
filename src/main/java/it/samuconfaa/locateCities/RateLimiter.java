package it.samuconfaa.locateCities;

import it.samuconfaa.locateCities.managers.ConfigManager;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {

    private final ConfigManager configManager;

    // Strutture ottimizzate con cleanup automatico
    private final ConcurrentMap<String, Long> lastSearchTime;
    private final ConcurrentMap<String, Long> lastTeleportTime;

    // Contatori per cleanup automatico
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());
    private static final long CLEANUP_INTERVAL = 300_000L; // 5 minuti
    private static final long ENTRY_TTL = 3600_000L; // 1 ora
    private static final int MAX_ENTRIES = 1000; // Limite massimo entry

    public RateLimiter(ConfigManager configManager) {
        this.configManager = configManager;

        // HashMap con dimensione iniziale ottimizzata
        this.lastSearchTime = new ConcurrentHashMap<>(64, 0.75f, 2);
        this.lastTeleportTime = new ConcurrentHashMap<>(64, 0.75f, 2);
    }

    public boolean canSearch(Player player) {
        if (!configManager.isRateLimitEnabled()) return true;
        if (player.hasPermission("locatecities.noratelimit")) return true;

        return checkAndUpdateLimit(
                lastSearchTime,
                player.getName(),
                configManager.getSearchCooldown() * 1000L
        );
    }

    public boolean canTeleport(Player player) {
        if (!configManager.isRateLimitEnabled()) return true;
        if (player.hasPermission("locatecities.noratelimit")) return true;

        return checkAndUpdateLimit(
                lastTeleportTime,
                player.getName(),
                configManager.getTeleportCooldown() * 1000L
        );
    }

    private boolean checkAndUpdateLimit(ConcurrentMap<String, Long> timeMap,
                                        String playerName, long cooldownMs) {

        // Cleanup periodico automatico per prevenire memory leaks
        long now = System.currentTimeMillis();
        if (now - lastCleanup.get() > CLEANUP_INTERVAL) {
            cleanupExpiredEntries();
        }

        // Controllo limite massimo entry per sicurezza
        if (timeMap.size() > MAX_ENTRIES) {
            cleanupOldestEntries(timeMap);
        }

        String sanitizedName = sanitizePlayerName(playerName);

        // Atomic check-and-update
        Long lastTime = timeMap.get(sanitizedName);
        if (lastTime == null || (now - lastTime) >= cooldownMs) {
            timeMap.put(sanitizedName, now);
            return true;
        }

        return false;
    }

    public int getRemainingSearchTime(Player player) {
        if (!configManager.isRateLimitEnabled()) return 0;
        if (player.hasPermission("locatecities.noratelimit")) return 0;

        return getRemainingTime(
                lastSearchTime,
                player.getName(),
                configManager.getSearchCooldown() * 1000L
        );
    }

    public int getRemainingTeleportTime(Player player) {
        if (!configManager.isRateLimitEnabled()) return 0;
        if (player.hasPermission("locatecities.noratelimit")) return 0;

        return getRemainingTime(
                lastTeleportTime,
                player.getName(),
                configManager.getTeleportCooldown() * 1000L
        );
    }

    private int getRemainingTime(ConcurrentMap<String, Long> timeMap,
                                 String playerName, long cooldownMs) {

        String sanitizedName = sanitizePlayerName(playerName);
        Long lastTime = timeMap.get(sanitizedName);

        if (lastTime == null) return 0;

        long now = System.currentTimeMillis();
        long remaining = cooldownMs - (now - lastTime);

        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    /**
     * Cleanup ottimizzato che rimuove entry scadute
     */
    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();

        // Evita cleanup multipli simultanei
        if (!lastCleanup.compareAndSet(lastCleanup.get(), now)) {
            return;
        }

        long expireTime = now - ENTRY_TTL;

        // Cleanup in parallelo per performance
        lastSearchTime.entrySet().removeIf(entry -> entry.getValue() < expireTime);
        lastTeleportTime.entrySet().removeIf(entry -> entry.getValue() < expireTime);
    }

    /**
     * Emergency cleanup quando si supera il limite massimo
     */
    private void cleanupOldestEntries(ConcurrentMap<String, Long> timeMap) {
        if (timeMap.size() <= MAX_ENTRIES) return;

        // Rimuovi il 20% delle entry più vecchie
        int toRemove = MAX_ENTRIES / 5;

        timeMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                .limit(toRemove)
                .map(entry -> entry.getKey())
                .forEach(timeMap::remove);
    }

    /**
     * Pulisce tutti i cooldown - mantiene API esistente
     */
    public void clearCooldowns() {
        lastSearchTime.clear();
        lastTeleportTime.clear();
        lastCleanup.set(System.currentTimeMillis());
    }

    /**
     * Sanitizzazione ottimizzata del nome giocatore
     */
    private String sanitizePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be null or empty");
        }

        String sanitized = playerName.trim().toLowerCase();

        if (sanitized.length() > 16) {
            sanitized = sanitized.substring(0, 16);
        }

        // Validazione più veloce senza regex
        StringBuilder sb = new StringBuilder(sanitized.length());
        for (char c : sanitized.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            }
        }

        String result = sb.toString();
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Player name contains only invalid characters");
        }

        return result.intern(); // Ottimizza memoria per nomi ricorrenti
    }
}