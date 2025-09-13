package it.samuconfaa.locateCities.database;

import it.samuconfaa.locateCities.LocateCities;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class DatabaseManager {

    private final LocateCities plugin;
    private Connection connection;
    private final File dbFile;
    private final Logger logger;
    private final ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock();

    // Pattern per validazione sicura dei nomi
    private static final Pattern SAFE_PLAYER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");
    private static final Pattern SAFE_CITY_NAME_PATTERN = Pattern.compile("^[a-zA-ZÀ-ÿ0-9\\s\\-'.,]{1,50}$");

    // Prepared statements per performance e sicurezza
    private PreparedStatement insertTeleportStmt;
    private PreparedStatement selectLastTeleportStmt;
    private PreparedStatement selectPlayerTeleportsStmt;
    private PreparedStatement deleteOldTeleportsStmt;

    public DatabaseManager(LocateCities plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dbFile = new File(plugin.getDataFolder(), "teleports.db");
        initDatabase();
        prepareStatements();
    }

    private void initDatabase() {
        dbLock.writeLock().lock();
        try {
            // Crea la directory se non esiste
            if (!plugin.getDataFolder().exists()) {
                if (!plugin.getDataFolder().mkdirs()) {
                    throw new RuntimeException("Impossibile creare la directory del plugin");
                }
            }

            // Connessione al database con parametri di sicurezza
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath() +
                    "?journal_mode=WAL&synchronous=NORMAL&foreign_keys=ON";
            connection = DriverManager.getConnection(url);

            // Configura connessione per sicurezza
            connection.setAutoCommit(true);

            // Crea la tabella se non esiste
            String createTable = """
                CREATE TABLE IF NOT EXISTS player_teleports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_name TEXT NOT NULL CHECK(length(player_name) <= 16),
                    city_name TEXT NOT NULL CHECK(length(city_name) <= 50),
                    teleport_date TEXT NOT NULL CHECK(date(teleport_date) IS NOT NULL),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(player_name, city_name, teleport_date)
                );
                """;

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTable);

                // Crea indici per performance
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_player_date 
                    ON player_teleports(player_name, teleport_date DESC)
                    """);

                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_city_name 
                    ON player_teleports(city_name)
                    """);
            }

            logger.info("Database SQLite inizializzato: " + dbFile.getName());

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Errore nell'inizializzazione del database", e);
            throw new RuntimeException("Impossibile inizializzare il database", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    private void prepareStatements() {
        dbLock.writeLock().lock();
        try {
            // Prepared statement per inserimento teleport
            insertTeleportStmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO player_teleports (player_name, city_name, teleport_date) 
                VALUES (?, ?, ?)
                """);

            // Prepared statement per ultimo teleport
            selectLastTeleportStmt = connection.prepareStatement("""
                SELECT city_name, teleport_date 
                FROM player_teleports 
                WHERE player_name = ? 
                ORDER BY teleport_date DESC 
                LIMIT 1
                """);

            // Prepared statement per teleport giocatore
            selectPlayerTeleportsStmt = connection.prepareStatement("""
                SELECT city_name, MAX(teleport_date) as last_teleport 
                FROM player_teleports 
                WHERE player_name = ? 
                GROUP BY city_name
                ORDER BY last_teleport DESC
                """);

            // Prepared statement per pulizia vecchi record
            deleteOldTeleportsStmt = connection.prepareStatement("""
                DELETE FROM player_teleports 
                WHERE teleport_date < ?
                """);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Errore nella preparazione degli statement", e);
            throw new RuntimeException("Impossibile preparare gli statement del database", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    /**
     * Controlla se il giocatore può fare QUALSIASI teleport (cooldown globale)
     */
    public boolean canTeleport(String playerName, int cooldownDays) {
        if (cooldownDays <= 0) return true;

        // Validazione input
        if (!isValidPlayerName(playerName)) {
            logger.warning("Tentativo di accesso con nome giocatore non valido: " + playerName);
            return false;
        }

        dbLock.readLock().lock();
        try {
            selectLastTeleportStmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = selectLastTeleportStmt.executeQuery()) {
                if (rs.next()) {
                    String lastTeleportStr = rs.getString("teleport_date");
                    if (lastTeleportStr != null) {
                        LocalDate lastTeleport = LocalDate.parse(lastTeleportStr);
                        LocalDate now = LocalDate.now();

                        return lastTeleport.plusDays(cooldownDays).isBefore(now) ||
                                lastTeleport.plusDays(cooldownDays).equals(now);
                    }
                }
                return true; // Prima volta che fa un teleport
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel controllo cooldown globale per: " + playerName, e);
            return true; // In caso di errore, permetti il teleport
        } finally {
            dbLock.readLock().unlock();
        }
    }

    /**
     * Ottiene i giorni rimanenti del cooldown globale
     */
    public int getRemainingDays(String playerName, int cooldownDays) {
        if (cooldownDays <= 0) return 0;

        // Validazione input
        if (!isValidPlayerName(playerName)) {
            return 0;
        }

        dbLock.readLock().lock();
        try {
            selectLastTeleportStmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = selectLastTeleportStmt.executeQuery()) {
                if (rs.next()) {
                    String lastTeleportStr = rs.getString("teleport_date");
                    if (lastTeleportStr != null) {
                        LocalDate lastTeleport = LocalDate.parse(lastTeleportStr);
                        LocalDate now = LocalDate.now();
                        LocalDate nextAvailable = lastTeleport.plusDays(cooldownDays);

                        if (nextAvailable.isAfter(now)) {
                            return (int) (nextAvailable.toEpochDay() - now.toEpochDay());
                        }
                    }
                }
                return 0;
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel calcolo giorni rimanenti per: " + playerName, e);
            return 0;
        } finally {
            dbLock.readLock().unlock();
        }
    }

    /**
     * Ottiene la data dell'ultimo teleport (qualsiasi città)
     */
    public LocalDate getLastTeleportDate(String playerName) {
        if (!isValidPlayerName(playerName)) {
            return null;
        }

        dbLock.readLock().lock();
        try {
            selectLastTeleportStmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = selectLastTeleportStmt.executeQuery()) {
                if (rs.next()) {
                    String dateStr = rs.getString("teleport_date");
                    if (dateStr != null) {
                        return LocalDate.parse(dateStr);
                    }
                }
                return null;
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel recupero ultimo teleport per: " + playerName, e);
            return null;
        } finally {
            dbLock.readLock().unlock();
        }
    }

    /**
     * Ottiene la città dell'ultimo teleport
     */
    public String getLastTeleportCity(String playerName) {
        if (!isValidPlayerName(playerName)) {
            return null;
        }

        dbLock.readLock().lock();
        try {
            selectLastTeleportStmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = selectLastTeleportStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("city_name");
                }
                return null;
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel recupero ultima città per: " + playerName, e);
            return null;
        } finally {
            dbLock.readLock().unlock();
        }
    }

    /**
     * Registra un nuovo teleport
     */
    public void recordTeleport(String playerName, String cityName) {
        // Validazione input rigorosa
        if (!isValidPlayerName(playerName)) {
            logger.warning("Tentativo di registrare teleport con nome giocatore non valido: " + playerName);
            return;
        }

        if (!isValidCityName(cityName)) {
            logger.warning("Tentativo di registrare teleport con nome città non valido: " + cityName);
            return;
        }

        dbLock.writeLock().lock();
        try {
            insertTeleportStmt.setString(1, sanitizePlayerName(playerName));
            insertTeleportStmt.setString(2, sanitizeCityName(cityName));
            insertTeleportStmt.setString(3, LocalDate.now().toString());

            int affected = insertTeleportStmt.executeUpdate();
            if (affected > 0) {
                logger.fine("Registrato teleport: " + playerName + " -> " + cityName);
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nella registrazione del teleport", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    /**
     * Ottiene tutti i teleport di un giocatore
     */
    public Map<String, LocalDate> getPlayerTeleports(String playerName) {
        Map<String, LocalDate> teleports = new HashMap<>();

        if (!isValidPlayerName(playerName)) {
            return teleports; // Restituisce mappa vuota per input non valido
        }

        dbLock.readLock().lock();
        try {
            selectPlayerTeleportsStmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = selectPlayerTeleportsStmt.executeQuery()) {
                while (rs.next()) {
                    String cityName = rs.getString("city_name");
                    String dateStr = rs.getString("last_teleport");

                    if (cityName != null && dateStr != null) {
                        try {
                            LocalDate date = LocalDate.parse(dateStr);
                            teleports.put(cityName, date);
                        } catch (Exception e) {
                            logger.warning("Data non valida nel database: " + dateStr);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel recupero teleport del giocatore: " + playerName, e);
        } finally {
            dbLock.readLock().unlock();
        }

        return teleports;
    }

    /**
     * Pulisce i record vecchi dal database
     */
    public void clearOldTeleports(int daysToKeep) {
        if (daysToKeep < 1) {
            logger.warning("Tentativo di pulizia con giorni non validi: " + daysToKeep);
            return;
        }

        dbLock.writeLock().lock();
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
            deleteOldTeleportsStmt.setString(1, cutoffDate.toString());

            int deleted = deleteOldTeleportsStmt.executeUpdate();
            if (deleted > 0) {
                logger.info("Eliminati " + deleted + " record di teleport più vecchi di " + daysToKeep + " giorni");
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nella pulizia dei teleport vecchi", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    /**
     * Chiude la connessione al database in modo sicuro
     */
    public void close() {
        dbLock.writeLock().lock();
        try {
            // Chiudi prepared statements
            closeStatement(insertTeleportStmt);
            closeStatement(selectLastTeleportStmt);
            closeStatement(selectPlayerTeleportsStmt);
            closeStatement(deleteOldTeleportsStmt);

            // Chiudi connessione
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Connessione database chiusa correttamente");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nella chiusura del database", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    private void closeStatement(PreparedStatement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Errore nella chiusura dello statement", e);
            }
        }
    }

    // Metodi di validazione e sanitizzazione
    private boolean isValidPlayerName(String playerName) {
        return playerName != null && SAFE_PLAYER_NAME_PATTERN.matcher(playerName).matches();
    }

    private boolean isValidCityName(String cityName) {
        return cityName != null && SAFE_CITY_NAME_PATTERN.matcher(cityName).matches();
    }

    private String sanitizePlayerName(String playerName) {
        if (playerName == null) return "";
        return playerName.trim().toLowerCase();
    }

    private String sanitizeCityName(String cityName) {
        if (cityName == null) return "";
        return cityName.trim().toLowerCase().substring(0, Math.min(cityName.length(), 50));
    }

    /**
     * Metodi deprecati mantenuti per compatibilità
     */
    @Deprecated
    public boolean canTeleportToCity(String playerName, String cityName, int cooldownDays) {
        return canTeleport(playerName, cooldownDays);
    }

    @Deprecated
    public int getRemainingDays(String playerName, String cityName, int cooldownDays) {
        return getRemainingDays(playerName, cooldownDays);
    }

    /**
     * Ottiene statistiche del database per debugging
     */
    public String getDatabaseStats() {
        dbLock.readLock().lock();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as total FROM player_teleports")) {

            if (rs.next()) {
                int totalRecords = rs.getInt("total");
                long dbSize = dbFile.length() / 1024; // KB

                return String.format("Database: %d record, %d KB", totalRecords, dbSize);
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel recupero statistiche database", e);
        } finally {
            dbLock.readLock().unlock();
        }

        return "Statistiche non disponibili";
    }

    /**
     * Verifica l'integrità del database
     */
    public boolean checkDatabaseIntegrity() {
        dbLock.readLock().lock();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {

            if (rs.next()) {
                String result = rs.getString(1);
                return "ok".equals(result);
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel controllo integrità database", e);
        } finally {
            dbLock.readLock().unlock();
        }

        return false;
    }
}