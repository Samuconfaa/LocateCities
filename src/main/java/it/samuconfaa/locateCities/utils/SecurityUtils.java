package it.samuconfaa.locateCities.utils;

import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Utility class per validazioni di sicurezza comuni
 */
public final class SecurityUtils {

    private SecurityUtils() {} // Prevent instantiation

    // Pattern di validazione
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");
    private static final Pattern CITY_NAME_PATTERN = Pattern.compile("^[a-zA-ZÀ-ÿ0-9\\s\\-'.,]{1,100}$");
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\p{P}\\p{Z}&&[^<>\"'&]]*$");

    // Limiti di sicurezza
    public static final int MAX_PLAYER_NAME_LENGTH = 16;
    public static final int MAX_CITY_NAME_LENGTH = 100;
    public static final int MAX_MESSAGE_LENGTH = 500;
    public static final int MAX_COORDINATE_PRECISION = 6; // decimal places

    private static final Logger logger = Logger.getLogger(SecurityUtils.class.getName());

    /**
     * Valida un nome giocatore secondo le regole Minecraft
     */
    public static boolean isValidPlayerName(String playerName) {
        return playerName != null &&
                playerName.length() <= MAX_PLAYER_NAME_LENGTH &&
                PLAYER_NAME_PATTERN.matcher(playerName).matches();
    }

    /**
     * Valida un nome città
     */
    public static boolean isValidCityName(String cityName) {
        return cityName != null &&
                !cityName.trim().isEmpty() &&
                cityName.length() <= MAX_CITY_NAME_LENGTH &&
                CITY_NAME_PATTERN.matcher(cityName).matches();
    }

    /**
     * Sanitizza un nome giocatore
     */
    public static String sanitizePlayerName(String playerName) {
        if (playerName == null) return "";

        String sanitized = playerName.trim().replaceAll("[^a-zA-Z0-9_-]", "");

        if (sanitized.length() > MAX_PLAYER_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_PLAYER_NAME_LENGTH);
        }

        return sanitized;
    }

    /**
     * Sanitizza un nome città
     */
    public static String sanitizeCityName(String cityName) {
        if (cityName == null) return "";

        String sanitized = cityName.trim();

        // Rimuovi caratteri potenzialmente pericolosi
        sanitized = sanitized.replaceAll("[<>\"'&]", "");

        if (sanitized.length() > MAX_CITY_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_CITY_NAME_LENGTH);
        }

        return sanitized;
    }

    /**
     * Valida coordinate geografiche
     */
    public static boolean isValidCoordinate(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) &&
                !Double.isInfinite(lat) && !Double.isInfinite(lon) &&
                lat >= -90.0 && lat <= 90.0 &&
                lon >= -180.0 && lon <= 180.0;
    }

    /**
     * Valida e arrotonda coordinate per evitare precisione eccessiva
     */
    public static double sanitizeCoordinate(double coordinate) {
        if (Double.isNaN(coordinate) || Double.isInfinite(coordinate)) {
            return 0.0;
        }

        // Arrotonda a 6 decimali per evitare precisione eccessiva
        double multiplier = Math.pow(10, MAX_COORDINATE_PRECISION);
        return Math.round(coordinate * multiplier) / multiplier;
    }

    /**
     * Valida una stringa generica per sicurezza
     */
    public static boolean isSafeString(String input) {
        if (input == null) return false;

        return input.length() <= MAX_MESSAGE_LENGTH &&
                SAFE_STRING_PATTERN.matcher(input).matches();
    }

    /**
     * Sanitizza una stringa generica
     */
    public static String sanitizeString(String input) {
        if (input == null) return "";

        String sanitized = input.trim();

        // Rimuovi caratteri HTML/XML pericolosi
        sanitized = sanitized.replaceAll("[<>\"'&]", "");

        // Rimuovi caratteri di controllo
        sanitized = sanitized.replaceAll("[\\p{Cntrl}]", "");

        if (sanitized.length() > MAX_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_MESSAGE_LENGTH);
        }

        return sanitized;
    }

    /**
     * Valida un range numerico
     */
    public static boolean isInRange(double value, double min, double max) {
        return !Double.isNaN(value) && !Double.isInfinite(value) &&
                value >= min && value <= max;
    }

    /**
     * Valida un timeout
     */
    public static boolean isValidTimeout(int timeout) {
        return timeout >= 1000 && timeout <= 60000; // 1-60 secondi
    }

    /**
     * Valida una scala
     */
    public static boolean isValidScale(double scale) {
        return isInRange(scale, 0.1, 1000000.0);
    }

    /**
     * Valida un costo economico
     */
    public static boolean isValidCost(double cost) {
        return isInRange(cost, 0.0, 1000000.0);
    }

    /**
     * Valida un cooldown in secondi
     */
    public static boolean isValidCooldown(int cooldown) {
        return cooldown >= 0 && cooldown <= 3600; // Max 1 ora
    }

    /**
     * Valida giorni di cooldown
     */
    public static boolean isValidDays(int days) {
        return days >= 1 && days <= 365; // 1 giorno - 1 anno
    }

    /**
     * Log sicuro che non espone informazioni sensibili
     */
    public static void logSecure(Logger logger, String level, String message, Object... params) {
        // Sanitizza i parametri prima del log
        Object[] sanitizedParams = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof String) {
                sanitizedParams[i] = sanitizeString((String) params[i]);
            } else {
                sanitizedParams[i] = params[i];
            }
        }

        String sanitizedMessage = sanitizeString(message);

        switch (level.toUpperCase()) {
            case "INFO":
                logger.info(String.format(sanitizedMessage, sanitizedParams));
                break;
            case "WARNING":
                logger.warning(String.format(sanitizedMessage, sanitizedParams));
                break;
            case "SEVERE":
                logger.severe(String.format(sanitizedMessage, sanitizedParams));
                break;
            default:
                logger.info(String.format(sanitizedMessage, sanitizedParams));
        }
    }

    /**
     * Verifica se una stringa contiene pattern sospetti
     */
    public static boolean containsSuspiciousPatterns(String input) {
        if (input == null) return false;

        String lower = input.toLowerCase();

        // Pattern sospetti
        String[] suspiciousPatterns = {
                "javascript:", "data:text/html", "<script", "<iframe",
                "onload=", "onerror=", "eval(", "document.cookie",
                "../", "..\\", "file://", "ftp://",
                "drop table", "delete from", "union select",
                "exec(", "system(", "cmd.exe", "/bin/sh"
        };

        for (String pattern : suspiciousPatterns) {
            if (lower.contains(pattern)) {
                logger.warning("Pattern sospetto rilevato: " + pattern);
                return true;
            }
        }

        return false;
    }

    /**
     * Genera un hash sicuro per cache key
     */
    public static String generateSafeCacheKey(String... components) {
        StringBuilder sb = new StringBuilder();
        for (String component : components) {
            if (component != null) {
                String sanitized = sanitizeString(component).toLowerCase();
                sb.append(sanitized).append("_");
            }
        }

        String key = sb.toString();
        return key.length() > 1 ? key.substring(0, key.length() - 1) : "default";
    }

    /**
     * Valida input per API esterne
     */
    public static boolean isValidApiInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String trimmed = input.trim();

        // Controlli base
        if (trimmed.length() > 100 || containsSuspiciousPatterns(trimmed)) {
            return false;
        }

        // Deve contenere almeno un carattere alfabetico
        if (!trimmed.matches(".*[a-zA-Z].*")) {
            return false;
        }

        return true;
    }

    /**
     * Rate limiting semplice basato su timestamp
     */
    public static class RateLimiter {
        private final java.util.concurrent.ConcurrentHashMap<String, Long> timestamps =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final long intervalMs;

        public RateLimiter(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public boolean allow(String key) {
            String safeKey = sanitizeString(key);
            long now = System.currentTimeMillis();
            Long last = timestamps.put(safeKey, now);

            return last == null || (now - last) >= intervalMs;
        }

        public void clear() {
            timestamps.clear();
        }
    }

    /**
     * Validazione combinata per città e giocatore
     */
    public static ValidationResult validateTeleportRequest(String playerName, String cityName) {
        ValidationResult result = new ValidationResult();

        if (!isValidPlayerName(playerName)) {
            result.addError("Nome giocatore non valido");
        }

        if (!isValidCityName(cityName)) {
            result.addError("Nome città non valido");
        }

        if (containsSuspiciousPatterns(playerName) || containsSuspiciousPatterns(cityName)) {
            result.addError("Input contiene pattern sospetti");
        }

        return result;
    }

    /**
     * Classe per risultati di validazione
     */
    public static class ValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        public void addError(String error) {
            errors.add(sanitizeString(error));
        }

        public void addWarning(String warning) {
            warnings.add(sanitizeString(warning));
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }

        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }

        public String getErrorMessage() {
            return String.join(", ", errors);
        }
    }
}