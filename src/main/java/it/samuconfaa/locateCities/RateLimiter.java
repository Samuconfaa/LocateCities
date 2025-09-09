package it.samuconfaa.locateCities;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class RateLimiter {

    private final ConfigManager configManager;
    private final Map<String, Long> lastSearchTime;
    private final Map<String, Long> lastTeleportTime;

    public RateLimiter(ConfigManager configManager) {
        this.configManager = configManager;
        this.lastSearchTime = new HashMap<>();
        this.lastTeleportTime = new HashMap<>();
    }

    public boolean canSearch(Player player) {
        if (!configManager.isRateLimitEnabled()) return true;
        if (player.hasPermission("locatecities.noratelimit")) return true;

        String playerName = player.getName();
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

        String playerName = player.getName();
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

        String playerName = player.getName();
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

        String playerName = player.getName();
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
}