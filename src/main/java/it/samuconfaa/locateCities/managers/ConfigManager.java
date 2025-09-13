package it.samuconfaa.locateCities.managers;

import it.samuconfaa.locateCities.LocateCities;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ConfigManager {

    private final LocateCities plugin;
    private FileConfiguration config;
    private final Logger logger;

    // Pattern per validazione sicura dei messaggi
    private static final Pattern SAFE_MESSAGE_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\p{P}\\p{Z}\\p{S}&&[^<>]]*$");
    private static final int MAX_MESSAGE_LENGTH = 500;

    public ConfigManager(LocateCities plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.logger = plugin.getLogger();
        setupDefaults();
    }

    private void setupDefaults() {
        // Coordinate defaults con validazione
        config.addDefault("lat_origin", 0.0);
        config.addDefault("lon_origin", 0.0);
        config.addDefault("scale", 100.0);
        config.addDefault("y_default", 64);

        // Feature defaults
        config.addDefault("enable_teleport", true);
        config.addDefault("use_terrain_height", true);
        config.addDefault("invert_x", false);
        config.addDefault("invert_z", false);

        // API defaults con limiti di sicurezza
        config.addDefault("api_timeout", 5000);
        config.addDefault("cache_duration_hours", 24);

        // Economy defaults
        config.addDefault("economy.enable", true);
        config.addDefault("economy.search_cost", 10.0);
        config.addDefault("economy.teleport_cost", 50.0);
        config.addDefault("economy.free_distance", 1000);

        // Rate limiting defaults
        config.addDefault("rate_limit.enabled", true);
        config.addDefault("rate_limit.search_cooldown", 3);
        config.addDefault("rate_limit.teleport_cooldown", 10);

        // Teleport day cooldown defaults
        config.addDefault("teleport_day_cooldown.enabled", true);
        config.addDefault("teleport_day_cooldown.days", 7);

        // Message defaults - AGGIORNATI per i nuovi comandi
        config.addDefault("messages.searching", "&e🔍 Ricerca di &f{city} &ein corso...");
        config.addDefault("messages.found", "&a📍 &f{city} &asi trova alle coordinate &bX:{x} Z:{z}");
        config.addDefault("messages.teleported", "&a✈ Teletrasportato a &f{city}&a!");
        config.addDefault("messages.not_found", "&c❌ Città non trovata: &f{city}");
        config.addDefault("messages.teleport_disabled", "&c❌ Il teletrasporto è disabilitato!");
        config.addDefault("messages.no_permission", "&c❌ Non hai il permesso per usare questo comando!");
        config.addDefault("messages.no_permission_teleport", "&c❌ Non hai il permesso per teletrasportarti!");
        config.addDefault("messages.rate_limited_search", "&c❌ Devi aspettare &f{seconds} &csecondi prima di cercare un'altra città!");
        config.addDefault("messages.rate_limited_teleport", "&c❌ Devi aspettare &f{seconds} &csecondi prima di teletrasportarti di nuovo!");
        config.addDefault("messages.insufficient_funds", "&c❌ Non hai abbastanza soldi! Serve: &f${cost}");
        config.addDefault("messages.economy_charged", "&a✅ Addebitati &f${cost} &aper la ricerca");
        config.addDefault("messages.teleport_charged", "&a✅ Addebitati &f${cost} &aper il teletrasporto");
        config.addDefault("messages.free_teleport", "&a✅ Teletrasporto gratuito (distanza: &f{distance} &ablocchi)");
        config.addDefault("messages.config_reloaded", "&a✅ Configurazione ricaricata!");
        config.addDefault("messages.cache_cleared", "&a✅ Cache pulita!");
        config.addDefault("messages.origin_set", "&a✅ Origine impostata a: &f{lat}, {lon}");
        config.addDefault("messages.scale_set", "&a✅ Scala impostata a: &f{scale}");
        config.addDefault("messages.invalid_coordinates", "&c❌ Coordinate non valide!");
        config.addDefault("messages.invalid_scale", "&c❌ Scala non valida!");
        config.addDefault("messages.error_general", "&c❌ Errore: &f{error}");
        config.addDefault("messages.teleport_day_cooldown", "&c❌ Puoi teletrasportarti a &f{city} &cfra &f{days} &cgiorni! (Ultimo: &f{last_date})");
        config.addDefault("messages.teleport_history_header", "&6╔══════════════════════════════════════╗\n&6║&e        📜 CRONOLOGIA TELEPORT 📜       &6║\n&6╚══════════════════════════════════════╝");
        config.addDefault("messages.teleport_history_entry", "&f{index}. &b{city} &7- &f{date} &7({days_ago} giorni fa)");
        config.addDefault("messages.teleport_history_empty", "&7Nessun teleport effettuato ancora.");
        config.addDefault("messages.teleport_history_footer", "&7Usa &a/citta tp <nome> &7per teletrasportarti!");
        config.addDefault("messages.cooldown_bypassed", "&a✅ Il cooldown per i teleport è stato resettato da un admin!");
        config.addDefault("messages.teleport_global_cooldown", "&c❌ Puoi teletrasportarti fra &f{days} &cgiorni! Ultimo: &b{last_city} &c(&f{last_date}&c)");

        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    // Coordinate methods con validazione migliorata
    public double getLatOrigin() {
        double lat = config.getDouble("lat_origin");
        return validateAndClampLatitude(lat);
    }

    public double getLonOrigin() {
        double lon = config.getDouble("lon_origin");
        return validateAndClampLongitude(lon);
    }

    public double getScale() {
        double scale = config.getDouble("scale");
        return validateAndClampScale(scale);
    }

    public int getDefaultY() {
        int y = config.getInt("y_default");
        return validateAndClampY(y);
    }

    public boolean isInvertX() {
        return config.getBoolean("invert_x");
    }

    public boolean isInvertZ() {
        return config.getBoolean("invert_z");
    }

    // Feature methods
    public boolean isTeleportEnabled() {
        return config.getBoolean("enable_teleport");
    }

    public boolean useTerrainHeight() {
        return config.getBoolean("use_terrain_height");
    }

    // API methods con validazione
    public int getApiTimeout() {
        int timeout = config.getInt("api_timeout");
        return validateAndClampTimeout(timeout);
    }

    public int getCacheDurationHours() {
        int hours = config.getInt("cache_duration_hours");
        return validateAndClampCacheHours(hours);
    }

    // Economy methods con validazione
    public boolean isEconomyEnabled() {
        return config.getBoolean("economy.enable");
    }

    public double getSearchCost() {
        double cost = config.getDouble("economy.search_cost");
        return validateAndClampCost(cost, "search_cost");
    }

    public double getTeleportCost() {
        double cost = config.getDouble("economy.teleport_cost");
        return validateAndClampCost(cost, "teleport_cost");
    }

    public int getFreeDistance() {
        int distance = config.getInt("economy.free_distance");
        return validateAndClampDistance(distance);
    }

    // Rate limiting methods con validazione
    public boolean isRateLimitEnabled() {
        return config.getBoolean("rate_limit.enabled");
    }

    public int getSearchCooldown() {
        int cooldown = config.getInt("rate_limit.search_cooldown");
        return validateAndClampCooldown(cooldown, "search_cooldown");
    }

    public int getTeleportCooldown() {
        int cooldown = config.getInt("rate_limit.teleport_cooldown");
        return validateAndClampCooldown(cooldown, "teleport_cooldown");
    }

    // Teleport day cooldown methods con validazione
    public boolean isTeleportDayCooldownEnabled() {
        return config.getBoolean("teleport_day_cooldown.enabled");
    }

    public int getTeleportCooldownDays() {
        int days = config.getInt("teleport_day_cooldown.days");
        return validateAndClampDays(days);
    }

    // Message methods con validazione di sicurezza
    public String getMessage(String key, String... placeholders) {
        String messageKey = "messages." + key;
        String message = config.getString(messageKey, "&cMessaggio non trovato: " + key);

        // Validazione sicurezza del messaggio
        message = validateAndSanitizeMessage(message, key);

        // Sostituisci placeholder con validazione
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String placeholder = placeholders[i];
                String value = placeholders[i + 1];

                // Validazione placeholder
                if (isValidPlaceholder(placeholder) && isValidPlaceholderValue(value)) {
                    message = message.replace("{" + placeholder + "}", value);
                }
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // Metodi di validazione migliorati
    public boolean validateCoordinates(double lat, double lon) {
        return isValidLatitude(lat) && isValidLongitude(lon);
    }

    public boolean validateScale(double scale) {
        return isValidScale(scale);
    }

    // Metodi di validazione privati
    private boolean isValidLatitude(double lat) {
        return !Double.isNaN(lat) && !Double.isInfinite(lat) && lat >= -90.0 && lat <= 90.0;
    }

    private boolean isValidLongitude(double lon) {
        return !Double.isNaN(lon) && !Double.isInfinite(lon) && lon >= -180.0 && lon <= 180.0;
    }

    private boolean isValidScale(double scale) {
        return !Double.isNaN(scale) && !Double.isInfinite(scale) && scale > 0 && scale <= 1000000;
    }

    private double validateAndClampLatitude(double lat) {
        if (!isValidLatitude(lat)) {
            logger.warning("Latitudine non valida nel config: " + lat + ", usando 0.0");
            return 0.0;
        }
        return lat;
    }

    private double validateAndClampLongitude(double lon) {
        if (!isValidLongitude(lon)) {
            logger.warning("Longitudine non valida nel config: " + lon + ", usando 0.0");
            return 0.0;
        }
        return lon;
    }

    private double validateAndClampScale(double scale) {
        if (!isValidScale(scale)) {
            logger.warning("Scala non valida nel config: " + scale + ", usando 100.0");
            return 100.0;
        }
        return scale;
    }

    private int validateAndClampY(int y) {
        if (y < -64 || y > 320) { // Minecraft world height limits
            logger.warning("Y di default non valido nel config: " + y + ", usando 64");
            return 64;
        }
        return y;
    }

    private int validateAndClampTimeout(int timeout) {
        if (timeout < 1000 || timeout > 30000) { // 1-30 secondi
            logger.warning("Timeout API non valido nel config: " + timeout + ", usando 5000ms");
            return 5000;
        }
        return timeout;
    }

    private int validateAndClampCacheHours(int hours) {
        if (hours < 1 || hours > 168) { // 1 ora - 1 settimana
            logger.warning("Durata cache non valida nel config: " + hours + ", usando 24h");
            return 24;
        }
        return hours;
    }

    private double validateAndClampCost(double cost, String costType) {
        if (Double.isNaN(cost) || Double.isInfinite(cost) || cost < 0 || cost > 1000000) {
            logger.warning("Costo " + costType + " non valido nel config: " + cost + ", usando 10.0");
            return 10.0;
        }
        return cost;
    }

    private int validateAndClampDistance(int distance) {
        if (distance < 0 || distance > 100000) { // Massimo 100k blocchi
            logger.warning("Distanza gratuita non valida nel config: " + distance + ", usando 1000");
            return 1000;
        }
        return distance;
    }

    private int validateAndClampCooldown(int cooldown, String cooldownType) {
        if (cooldown < 0 || cooldown > 3600) { // Massimo 1 ora
            logger.warning("Cooldown " + cooldownType + " non valido nel config: " + cooldown + ", usando 10s");
            return 10;
        }
        return cooldown;
    }

    private int validateAndClampDays(int days) {
        if (days < 1 || days > 365) { // 1 giorno - 1 anno
            logger.warning("Giorni cooldown non validi nel config: " + days + ", usando 7");
            return 7;
        }
        return days;
    }

    private String validateAndSanitizeMessage(String message, String key) {
        if (message == null || message.trim().isEmpty()) {
            logger.warning("Messaggio vuoto per chiave: " + key);
            return "&cMessaggio non disponibile";
        }

        // Controlla lunghezza
        if (message.length() > MAX_MESSAGE_LENGTH) {
            logger.warning("Messaggio troppo lungo per chiave: " + key + ", troncato");
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }

        // Validazione pattern sicuro (rimuove potenziali caratteri pericolosi)
        if (!SAFE_MESSAGE_PATTERN.matcher(message).matches()) {
            logger.warning("Messaggio contiene caratteri non sicuri per chiave: " + key);
            // Rimuove caratteri non sicuri
            message = message.replaceAll("[<>]", "");
        }

        return message;
    }

    private boolean isValidPlaceholder(String placeholder) {
        if (placeholder == null || placeholder.trim().isEmpty()) {
            return false;
        }

        // Consenti solo placeholder alfanumerici con underscore
        return placeholder.matches("^[a-zA-Z0-9_]+$") && placeholder.length() <= 20;
    }

    private boolean isValidPlaceholderValue(String value) {
        if (value == null) {
            return false;
        }

        // Limita lunghezza e rimuove caratteri potenzialmente pericolosi
        if (value.length() > 100) {
            return false;
        }

        // Evita caratteri HTML/script pericolosi
        return !value.contains("<script>") &&
                !value.contains("<iframe>") &&
                !value.contains("javascript:") &&
                !value.contains("data:text/html");
    }

    public void reload() {
        try {
            plugin.reloadConfig();
            this.config = plugin.getConfig();
            logger.info("Configurazione ricaricata con successo");

            // Riesegui validazione dopo reload
            validateConfigurationOnLoad();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Errore nel reload della configurazione", e);
            throw new RuntimeException("Impossibile ricaricare la configurazione: " + e.getMessage());
        }
    }

    /**
     * Valida la configurazione al caricamento
     */
    private void validateConfigurationOnLoad() {
        boolean hasErrors = false;

        // Valida coordinate
        if (!validateCoordinates(config.getDouble("lat_origin"), config.getDouble("lon_origin"))) {
            logger.warning("Coordinate di origine non valide nella configurazione");
            hasErrors = true;
        }

        // Valida scala
        if (!validateScale(config.getDouble("scale"))) {
            logger.warning("Scala non valida nella configurazione");
            hasErrors = true;
        }

        // Valida timeout API
        int timeout = config.getInt("api_timeout");
        if (timeout < 1000 || timeout > 30000) {
            logger.warning("Timeout API non valido nella configurazione");
            hasErrors = true;
        }

        // Valida costi economy
        if (config.getDouble("economy.search_cost") < 0 ||
                config.getDouble("economy.teleport_cost") < 0) {
            logger.warning("Costi economy non validi nella configurazione");
            hasErrors = true;
        }

        if (hasErrors) {
            logger.warning("Configurazione contiene errori - alcuni valori verranno sostituiti con i default");
        } else {
            logger.info("Validazione configurazione completata con successo");
        }
    }

    /**
     * Ottiene informazioni di debug sulla configurazione
     */
    public String getConfigurationDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== CONFIGURAZIONE DEBUG ===\n");
        info.append("Coordinate origine: ").append(getLatOrigin()).append(", ").append(getLonOrigin()).append("\n");
        info.append("Scala: ").append(getScale()).append("\n");
        info.append("Timeout API: ").append(getApiTimeout()).append("ms\n");
        info.append("Cache durata: ").append(getCacheDurationHours()).append("h\n");
        info.append("Economy abilitata: ").append(isEconomyEnabled()).append("\n");
        info.append("Rate limit abilitato: ").append(isRateLimitEnabled()).append("\n");
        info.append("Cooldown giorni abilitato: ").append(isTeleportDayCooldownEnabled()).append("\n");
        return info.toString();
    }
}