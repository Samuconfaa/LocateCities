package it.samuconfaa.locateCities.data;

import it.samuconfaa.locateCities.managers.ConfigManager;

public class CityData {

    private final String name;
    private final double latitude;
    private final double longitude;
    private final long timestamp;

    public CityData(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = System.currentTimeMillis();
    }

    public CityData(String name, double latitude, double longitude, long timestamp) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired(int cacheHours) {
        long cacheTimeMs = cacheHours * 60 * 60 * 1000L;
        return System.currentTimeMillis() - timestamp > cacheTimeMs;
    }

    public MinecraftCoordinates toMinecraftCoordinates(ConfigManager config) {
        double latOrigin = config.getLatOrigin();
        double lonOrigin = config.getLonOrigin();
        double scale = config.getScale();

        // Conversione coordinate reali -> Minecraft
        // Calcolo base
        double deltaLon = longitude - lonOrigin;
        double deltaLat = latOrigin - latitude; // Invertito per Minecraft (Z cresce verso sud)

        int x = (int) Math.round(deltaLon * scale);
        int z = (int) Math.round(deltaLat * scale);

        return new MinecraftCoordinates(x, z, config.getDefaultY());
    }

    public static class MinecraftCoordinates {
        private final int x;
        private final int z;
        private final int y;

        public MinecraftCoordinates(int x, int z, int y) {
            this.x = x;
            this.z = z;
            this.y = y;
        }

        public int getX() { return x; }
        public int getZ() { return z; }
        public int getY() { return y; }

        public MinecraftCoordinates withY(int newY) {
            return new MinecraftCoordinates(x, z, newY);
        }

        @Override
        public String toString() {
            return String.format("X:%d Z:%d Y:%d", x, z, y);
        }
    }
}