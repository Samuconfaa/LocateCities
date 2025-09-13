package it.samuconfaa.locateCities;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {

    private final LocateCities plugin;
    private Connection connection;
    private final File dbFile;

    public DatabaseManager(LocateCities plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "teleports.db");
        initDatabase();
    }

    private void initDatabase() {
        try {
            // Crea la directory se non esiste
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Connessione al database
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // Crea la tabella se non esiste
            String createTable = """
                CREATE TABLE IF NOT EXISTS player_teleports (
                    player_name TEXT NOT NULL,
                    city_name TEXT NOT NULL,
                    teleport_date TEXT NOT NULL,
                    PRIMARY KEY (player_name, city_name, teleport_date)
                );
                """;

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTable);
            }

            plugin.getLogger().info("Database SQLite inizializzato: " + dbFile.getName());

        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nell'inizializzazione del database: " + e.getMessage());
        }
    }

    public boolean canTeleportToCity(String playerName, String cityName, int cooldownDays) {
        if (cooldownDays <= 0) return true; // Nessun cooldown

        String query = """
            SELECT teleport_date FROM player_teleports 
            WHERE player_name = ? AND city_name = ? 
            ORDER BY teleport_date DESC LIMIT 1
            """;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerName);
            stmt.setString(2, cityName.toLowerCase());

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastTeleportStr = rs.getString("teleport_date");
                LocalDate lastTeleport = LocalDate.parse(lastTeleportStr);
                LocalDate now = LocalDate.now();

                return lastTeleport.plusDays(cooldownDays).isBefore(now) ||
                        lastTeleport.plusDays(cooldownDays).equals(now);
            }

            return true; // Prima volta che si teletrasporta a questa città

        } catch (SQLException e) {
            plugin.getLogger().warning("Errore nel controllo cooldown teleport: " + e.getMessage());
            return true; // In caso di errore, permetti il teleport
        }
    }

    public void recordTeleport(String playerName, String cityName) {
        String insert = """
            INSERT OR REPLACE INTO player_teleports (player_name, city_name, teleport_date) 
            VALUES (?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(insert)) {
            stmt.setString(1, playerName);
            stmt.setString(2, cityName.toLowerCase());
            stmt.setString(3, LocalDate.now().toString());

            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("Errore nella registrazione del teleport: " + e.getMessage());
        }
    }

    public int getRemainingDays(String playerName, String cityName, int cooldownDays) {
        if (cooldownDays <= 0) return 0;

        String query = """
            SELECT teleport_date FROM player_teleports 
            WHERE player_name = ? AND city_name = ? 
            ORDER BY teleport_date DESC LIMIT 1
            """;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerName);
            stmt.setString(2, cityName.toLowerCase());

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String lastTeleportStr = rs.getString("teleport_date");
                LocalDate lastTeleport = LocalDate.parse(lastTeleportStr);
                LocalDate now = LocalDate.now();
                LocalDate nextAvailable = lastTeleport.plusDays(cooldownDays);

                if (nextAvailable.isAfter(now)) {
                    return (int) (nextAvailable.toEpochDay() - now.toEpochDay());
                }
            }

            return 0;

        } catch (SQLException e) {
            plugin.getLogger().warning("Errore nel calcolo giorni rimanenti: " + e.getMessage());
            return 0;
        }
    }

    public Map<String, LocalDate> getPlayerTeleports(String playerName) {
        Map<String, LocalDate> teleports = new HashMap<>();

        String query = """
            SELECT city_name, MAX(teleport_date) as last_teleport 
            FROM player_teleports 
            WHERE player_name = ? 
            GROUP BY city_name
            ORDER BY last_teleport DESC
            """;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerName);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String cityName = rs.getString("city_name");
                LocalDate date = LocalDate.parse(rs.getString("last_teleport"));
                teleports.put(cityName, date);
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Errore nel recupero teleport del giocatore: " + e.getMessage());
        }

        return teleports;
    }

    public void clearOldTeleports(int daysToKeep) {
        String delete = """
            DELETE FROM player_teleports 
            WHERE teleport_date < ?
            """;

        try (PreparedStatement stmt = connection.prepareStatement(delete)) {
            LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
            stmt.setString(1, cutoffDate.toString());

            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Eliminati " + deleted + " record di teleport più vecchi di " + daysToKeep + " giorni");
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Errore nella pulizia dei teleport vecchi: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Errore nella chiusura del database: " + e.getMessage());
        }
    }
}