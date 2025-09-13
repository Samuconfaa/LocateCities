package it.samuconfaa.locateCities.database;

import it.samuconfaa.locateCities.LocateCities;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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

    // Pattern per validazione sicura
    private static final Pattern SAFE_PLAYER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");
    private static final Pattern SAFE_CITY_NAME_PATTERN = Pattern.compile("^[a-zA-ZÀ-ÿ0-9\\s\\-'.,]{1,50}$");

    // Connessione pool simulato per SQLite
    private final BlockingQueue<Connection> connectionPool;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private static final int MAX_POOL_SIZE = 3;

    // Batch operations per performance
    private final BlockingQueue<TeleportRecord> pendingInserts;
    private final ScheduledExecutorService batchProcessor;
    private final AtomicInteger batchedOperations = new AtomicInteger(0);

    // Prepared statements cache
    private Map<String, PreparedStatement> statementCache = new ConcurrentHashMap<>();

    // Metriche performance
    private final AtomicInteger totalQueries = new AtomicInteger(0);

    private static final int BATCH_SIZE = 50;
    private static final int BATCH_TIMEOUT_MS = 30000; // 30 secondi

    public DatabaseManager(LocateCities plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dbFile = new File(plugin.getDataFolder(), "teleports.db");

        this.connectionPool = new LinkedBlockingQueue<>(MAX_POOL_SIZE);
        this.pendingInserts = new LinkedBlockingQueue<>();
        this.statementCache = new ConcurrentHashMap<>();

        // Batch processor ottimizzato
        this.batchProcessor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LocateCities-Batch");
            t.setDaemon(true);
            return t;
        });

        initDatabase();
        startBatchProcessor();
    }

    private void initDatabase() {
        dbLock.writeLock().lock();
        try {
            if (!plugin.getDataFolder().exists()) {
                if (!plugin.getDataFolder().mkdirs()) {
                    throw new RuntimeException("Impossibile creare la directory del plugin");
                }
            }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath() +
                    "?journal_mode=WAL&synchronous=NORMAL&temp_store=MEMORY&cache_size=10000&foreign_keys=ON";
            connection = DriverManager.getConnection(url);

            // Ottimizzazioni SQLite
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA cache_size=10000");
                stmt.execute("PRAGMA foreign_keys=ON");
            }

            createOptimizedTables();
            prepareStatements();
            initConnectionPool();

            logger.info("Database SQLite ottimizzato inizializzato: " + dbFile.getName());

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Errore nell'inizializzazione del database", e);
            throw new RuntimeException("Impossibile inizializzare il database", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    private void createOptimizedTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // CORREZIONE 1: Aggiunto AUTOINCREMENT e rimosso WITHOUT ROWID per evitare l'errore NOT NULL
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_teleports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_name TEXT NOT NULL CHECK(length(player_name) <= 16),
                        city_name TEXT NOT NULL CHECK(length(city_name) <= 50),
                        teleport_date TEXT NOT NULL CHECK(date(teleport_date) IS NOT NULL),
                        created_at INTEGER DEFAULT (strftime('%s', 'now')),
                        UNIQUE(player_name, teleport_date) ON CONFLICT REPLACE
                    )
                    """);

            // Indici ottimizzati per query più frequenti
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_player_date_optimized 
                    ON player_teleports(player_name, teleport_date DESC, city_name)
                    """);

            // CORREZIONE 2: Rimossa la clausola WHERE per risolvere l'errore "non-deterministic use of date()"
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_date_only 
                    ON player_teleports(teleport_date)
                    """);
        }
    }

    private void initConnectionPool() {
        try {
            // Crea pool di connessioni per operazioni concorrenti
            for (int i = 0; i < MAX_POOL_SIZE - 1; i++) {
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath() +
                        "?journal_mode=WAL&synchronous=NORMAL&temp_store=MEMORY&cache_size=5000";
                Connection poolConn = DriverManager.getConnection(url);
                connectionPool.offer(poolConn);
            }
        } catch (SQLException e) {
            logger.warning("Errore inizializzazione connection pool: " + e.getMessage());
        }
    }

    private Connection getPooledConnection() throws SQLException {
        Connection poolConn = connectionPool.poll();
        if (poolConn == null || poolConn.isClosed()) {
            // Fallback alla connessione principale
            return connection;
        }
        return poolConn;
    }

    private void returnPooledConnection(Connection conn) {
        if (conn != connection && !connectionPool.offer(conn)) {
            try {
                conn.close();
            } catch (SQLException e) {
                // Ignora errori di chiusura
            }
        }
    }

    private void prepareStatements() throws SQLException {
        // Cache prepared statements per riutilizzo
        statementCache.put("SELECT_LAST_TELEPORT", connection.prepareStatement("""
                SELECT city_name, teleport_date FROM player_teleports 
                WHERE player_name = ? ORDER BY teleport_date DESC LIMIT 1
                """));

        statementCache.put("SELECT_PLAYER_TELEPORTS", connection.prepareStatement("""
                SELECT city_name, MAX(teleport_date) as last_teleport 
                FROM player_teleports WHERE player_name = ? 
                GROUP BY city_name ORDER BY last_teleport DESC
                """));

        statementCache.put("INSERT_TELEPORT", connection.prepareStatement("""
                INSERT OR REPLACE INTO player_teleports (player_name, city_name, teleport_date) 
                VALUES (?, ?, ?)
                """));

        statementCache.put("DELETE_OLD", connection.prepareStatement("""
                DELETE FROM player_teleports WHERE teleport_date < ?
                """));
    }

    private void startBatchProcessor() {
        // Processor che gira ogni 15 secondi
        batchProcessor.scheduleWithFixedDelay(this::processBatchInserts,
                15, 15, TimeUnit.SECONDS);
    }

    private void processBatchInserts() {
        if (pendingInserts.isEmpty()) return;

        dbLock.writeLock().lock();
        try {
            // CORREZIONE 3: Rimosso il try-with-resources per evitare di chiudere lo statement dalla cache
            PreparedStatement stmt = statementCache.get("INSERT_TELEPORT");
            connection.setAutoCommit(false);

            int processed = 0;
            TeleportRecord record;

            // Processa batch
            while ((record = pendingInserts.poll()) != null && processed < BATCH_SIZE) {
                stmt.setString(1, record.playerName);
                stmt.setString(2, record.cityName);
                stmt.setString(3, record.date.toString());
                stmt.addBatch();
                processed++;
            }

            if (processed > 0) {
                stmt.executeBatch();
                connection.commit();
                batchedOperations.addAndGet(processed);

                logger.fine("Processed " + processed + " teleport records in batch");
            }

        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.log(Level.WARNING, "Rollback failed", rollbackEx);
            }
            logger.log(Level.WARNING, "Batch insert failed", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.warning("Error resetting auto-commit: " + e.getMessage());
            }
            dbLock.writeLock().unlock();
        }
    }

    /**
     * Controlla se il giocatore può fare QUALSIASI teleport (cooldown globale) - OTTIMIZZATO
     */
    public boolean canTeleport(String playerName, int cooldownDays) {
        if (cooldownDays <= 0) return true;

        if (!isValidPlayerName(playerName)) {
            logger.warning("Tentativo di accesso con nome giocatore non valido: " + playerName);
            return false;
        }

        totalQueries.incrementAndGet();
        dbLock.readLock().lock();
        try {
            PreparedStatement stmt = statementCache.get("SELECT_LAST_TELEPORT");
            stmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String lastTeleportStr = rs.getString("teleport_date");
                    if (lastTeleportStr != null) {
                        LocalDate lastTeleport = LocalDate.parse(lastTeleportStr);
                        LocalDate now = LocalDate.now();

                        return lastTeleport.plusDays(cooldownDays).isBefore(now) ||
                                lastTeleport.plusDays(cooldownDays).equals(now);
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel controllo cooldown per: " + playerName, e);
            return true; // In caso di errore, permetti il teleport
        } finally {
            dbLock.readLock().unlock();
        }
    }

    /**
     * Ottiene i giorni rimanenti del cooldown globale - OTTIMIZZATO
     */
    public int getRemainingDays(String playerName, int cooldownDays) {
        if (cooldownDays <= 0 || !isValidPlayerName(playerName)) return 0;

        totalQueries.incrementAndGet();
        dbLock.readLock().lock();
        try {
            PreparedStatement stmt = statementCache.get("SELECT_LAST_TELEPORT");
            stmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = stmt.executeQuery()) {
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
     * Ottiene la data dell'ultimo teleport - OTTIMIZZATO con cache
     */
    public LocalDate getLastTeleportDate(String playerName) {
        if (!isValidPlayerName(playerName)) return null;

        totalQueries.incrementAndGet();
        dbLock.readLock().lock();
        try {
            PreparedStatement stmt = statementCache.get("SELECT_LAST_TELEPORT");
            stmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String dateStr = rs.getString("teleport_date");
                    return dateStr != null ? LocalDate.parse(dateStr) : null;
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
     * Ottiene la città dell'ultimo teleport - OTTIMIZZATO
     */
    public String getLastTeleportCity(String playerName) {
        if (!isValidPlayerName(playerName)) return null;

        totalQueries.incrementAndGet();
        dbLock.readLock().lock();
        try {
            PreparedStatement stmt = statementCache.get("SELECT_LAST_TELEPORT");
            stmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("city_name") : null;
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nel recupero ultima città per: " + playerName, e);
            return null;
        } finally {
            dbLock.readLock().unlock();
        }
    }

    /**
     * Registra un nuovo teleport - OTTIMIZZATO con batch processing
     */
    public void recordTeleport(String playerName, String cityName) {
        if (!isValidPlayerName(playerName) || !isValidCityName(cityName)) {
            logger.warning("Tentativo di registrare teleport con dati non validi: " +
                    playerName + " -> " + cityName);
            return;
        }

        // Aggiunge alla coda per batch processing
        TeleportRecord record = new TeleportRecord(
                sanitizePlayerName(playerName),
                sanitizeCityName(cityName),
                LocalDate.now()
        );

        if (!pendingInserts.offer(record)) {
            logger.warning("Failed to queue teleport record - queue full");
            // Fallback: inserimento diretto
            insertTeleportDirect(record);
        }
    }

    private void insertTeleportDirect(TeleportRecord record) {
        dbLock.writeLock().lock();
        try {
            PreparedStatement stmt = statementCache.get("INSERT_TELEPORT");
            stmt.setString(1, record.playerName);
            stmt.setString(2, record.cityName);
            stmt.setString(3, record.date.toString());

            stmt.executeUpdate();
            logger.fine("Direct insert: " + record.playerName + " -> " + record.cityName);

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nell'inserimento diretto teleport", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    /**
     * Ottiene tutti i teleport di un giocatore - OTTIMIZZATO
     */
    public Map<String, LocalDate> getPlayerTeleports(String playerName) {
        Map<String, LocalDate> teleports = new HashMap<>();
        if (!isValidPlayerName(playerName)) return teleports;

        totalQueries.incrementAndGet();
        dbLock.readLock().lock();
        try {
            PreparedStatement stmt = statementCache.get("SELECT_PLAYER_TELEPORTS");
            stmt.setString(1, sanitizePlayerName(playerName));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String cityName = rs.getString("city_name");
                    String dateStr = rs.getString("last_teleport");

                    if (cityName != null && dateStr != null) {
                        try {
                            teleports.put(cityName, LocalDate.parse(dateStr));
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
     * Pulisce i record vecchi - OTTIMIZZATO con batch delete
     */
    public void clearOldTeleports(int daysToKeep) {
        if (daysToKeep < 1) return;

        // Forza processamento batch pendenti prima della pulizia
        processBatchInserts();

        dbLock.writeLock().lock();
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
            PreparedStatement stmt = statementCache.get("DELETE_OLD");
            stmt.setString(1, cutoffDate.toString());

            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                logger.info("Eliminati " + deleted + " record più vecchi di " + daysToKeep + " giorni");

                // CORREZIONE 4: Sostituito PRAGMA optimize con VACUUM
                try (Statement optimizeStmt = connection.createStatement()) {
                    optimizeStmt.execute("VACUUM");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nella pulizia dei teleport vecchi", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    /**
     * Chiude il database in modo ottimizzato
     */
    public void close() {
        // Ferma batch processor
        batchProcessor.shutdown();
        try {
            if (!batchProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                batchProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Processa batch rimanenti
        processBatchInserts();

        dbLock.writeLock().lock();
        try {
            // Chiudi prepared statements
            statementCache.values().forEach(stmt -> {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    logger.warning("Error closing prepared statement: " + e.getMessage());
                }
            });
            statementCache.clear();

            // Chiudi connection pool
            Connection poolConn;
            while ((poolConn = connectionPool.poll()) != null) {
                try {
                    poolConn.close();
                } catch (SQLException e) {
                    logger.warning("Error closing pooled connection: " + e.getMessage());
                }
            }

            // Chiudi connessione principale
            if (connection != null && !connection.isClosed()) {
                // CORREZIONE 4: Sostituito PRAGMA optimize con VACUUM prima della chiusura
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("VACUUM");
                }
                connection.close();
                logger.info("Database chiuso correttamente");
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore nella chiusura del database", e);
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    // Metodi di validazione ottimizzati
    private boolean isValidPlayerName(String playerName) {
        return playerName != null && SAFE_PLAYER_NAME_PATTERN.matcher(playerName).matches();
    }

    private boolean isValidCityName(String cityName) {
        return cityName != null && SAFE_CITY_NAME_PATTERN.matcher(cityName).matches();
    }

    private String sanitizePlayerName(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase();
    }

    private String sanitizeCityName(String cityName) {
        if (cityName == null) return "";
        String sanitized = cityName.trim().toLowerCase();
        return sanitized.length() > 50 ? sanitized.substring(0, 50) : sanitized;
    }

    /**
     * Statistiche database ottimizzate
     */
    public String getDatabaseStats() {
        dbLock.readLock().lock();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as total FROM player_teleports")) {

            if (rs.next()) {
                int totalRecords = rs.getInt("total");
                long dbSize = dbFile.length() / 1024; // KB
                int queries = totalQueries.get();
                int batched = batchedOperations.get();

                return String.format("DB: %d records, %d KB, %d queries, %d batched",
                        totalRecords, dbSize, queries, batched);
            }

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore statistiche database", e);
        } finally {
            dbLock.readLock().unlock();
        }

        return "Statistiche non disponibili";
    }

    public boolean checkDatabaseIntegrity() {
        dbLock.readLock().lock();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {

            return rs.next() && "ok".equals(rs.getString(1));

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Errore controllo integrità", e);
            return false;
        } finally {
            dbLock.readLock().unlock();
        }
    }

    // Metodi deprecati per compatibilità
    @Deprecated
    public boolean canTeleportToCity(String playerName, String cityName, int cooldownDays) {
        return canTeleport(playerName, cooldownDays);
    }

    @Deprecated
    public int getRemainingDays(String playerName, String cityName, int cooldownDays) {
        return getRemainingDays(playerName, cooldownDays);
    }

    // Classe helper per batch operations
    private static class TeleportRecord {
        final String playerName;
        final String cityName;
        final LocalDate date;

        TeleportRecord(String playerName, String cityName, LocalDate date) {
            this.playerName = playerName;
            this.cityName = cityName;
            this.date = date;
        }
    }
}