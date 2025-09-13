package it.samuconfaa.locateCities;

import it.samuconfaa.locateCities.managers.ConfigManager;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RateLimiter {

    private final ConfigManager configManager;
    private final ConcurrentMap<String, Long> lastSearchTime;
    private final ConcurrentMap<String, Long> lastTeleportTime;

    public RateLimiter(ConfigManager configManager) {
        this.configManager = configManager;
        this.lastSearchTime = new ConcurrentHashMap<>();
        this.lastTeleportTime = new ConcurrentHashMap<>();
    }

    public boolean canSearch(Player player) {
        if (!configManager.isRateLimitEnabled()) return true;
        if (player.hasPermission("locatecities.noratelimit")) return true;

        String playerName = sanitizePlayerName(player.getName());
        long now = System.currentTimeMillis();
        long cooldown = configManager.getSearchCooldown() * 1000L;

        Long lastTime = lastSearchTime.get(playerName);
        if (lastTime == null || (now - lastTime) >= cooldown) {
            lastSearchTime.put(playerName, now);
            return true;
        }

        return false;
    }

    public boolean canTeleport(Player player) {
        if (!configManager.isRateLimitEnabled()) return true;
        if (player.hasPermission("locatecities.noratelimit")) return true;

        String playerName = sanitizePlayerName(player.getName());
        long now = System.currentTimeMillis();
        long cooldown = configManager.getTeleportCooldown() * 1000L;

        Long lastTime = lastTeleportTime.get(playerName);
        if (lastTime == null || (now - lastTime) >= cooldown) {
            lastTeleportTime.put(playerName, now);
            return true;
        }

        return false;
    }

    public int getRemainingSearchTime(Player player) {
        if (!configManager.isRateLimitEnabled()) return 0;
        if (player.hasPermission("locatecities.noratelimit")) return 0;

        String playerName = sanitizePlayerName(player.getName());
        long now = System.currentTimeMillis();
        long cooldown = configManager.getSearchCooldown() * 1000L;

        Long lastTime = lastSearchTime.get(playerName);
        if (lastTime == null) return 0;

        long remaining = cooldown - (now - lastTime);
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    public int getRemainingTeleportTime(Player player) {
        if (!configManager.isRateLimitEnabled()) return 0;
        if (player.hasPermission("locatecities.noratelimit")) return 0;

        String playerName = sanitizePlayerName(player.getName());
        long now = System.currentTimeMillis();
        long cooldown = configManager.getTeleportCooldown() * 1000L;

        Long lastTime = lastTeleportTime.get(playerName);
        if (lastTime == null) return 0;

        long remaining = cooldown - (now - lastTime);
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    public void clearCooldowns() {
        lastSearchTime.clear();
        lastTeleportTime.clear();
    }

    /**
     * Sanitizza il nome del giocatore per prevenire problemi di sicurezza
     * @param playerName nome del giocatore grezzo
     * @return nome sanitizzato
     */
    private String sanitizePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be null or empty");
        }

        // Rimuove caratteri speciali e limita la lunghezza
        String sanitized = playerName.trim().replaceAll("[^a-zA-Z0-9_-]", "");

        if (sanitized.length() > 16) { // Minecraft username limit
            sanitized = sanitized.substring(0, 16);
        }

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Player name contains only invalid characters");
        }

        return sanitized;
    }
}