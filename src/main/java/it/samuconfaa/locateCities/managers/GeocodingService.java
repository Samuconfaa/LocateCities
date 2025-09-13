package it.samuconfaa.locateCities.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.samuconfaa.locateCities.data.CityData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "LocateCities-Minecraft-Plugin/1.0";

    // Rate limiting più sicuro per Nominatim (1 richiesta ogni 1.5 secondi)
    private static final long RATE_LIMIT_MS = 1500;
    private static final long BURST_PROTECTION_MS = 5000; // 5 secondi se troppe richieste

    // Limite massimo richieste per ora (Nominatim policy)
    private static final int MAX_REQUESTS_PER_HOUR = 3600;

    private final int timeout;
    private final ConcurrentMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private final AtomicLong globalLastRequest = new AtomicLong(0);
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong hourlyRequestReset = new AtomicLong(System.currentTimeMillis() + 3600000);
    private final Logger logger = Logger.getLogger(GeocodingService.class.getName());

    private volatile boolean rateLimitExceeded = false;
    private volatile long rateLimitResetTime = 0;

    public GeocodingService(int timeout) {
        this.timeout = Math.max(timeout, 3000); // Minimo 3 secondi timeout
    }

    public CompletableFuture<CityData> searchCity(String cityName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validazione input
                if (!isValidCityName(cityName)) {
                    throw new IllegalArgumentException("Nome città non valido: " + cityName);
                }

                // Controllo rate limiting avanzato
                if (!checkRateLimit()) {
                    long waitTime = (rateLimitResetTime - System.currentTimeMillis()) / 1000;
                    throw new RuntimeException("Rate limit superato. Riprova tra " + waitTime + " secondi.");
                }

                return searchCitySync(cityName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Errore nella ricerca della città: " + cityName, e);
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Errore nella ricerca della città: " + e.getMessage(), e);
            }
        });
    }

    private boolean checkRateLimit() {
        long now = System.currentTimeMillis();

        // Reset contatore orario
        if (now > hourlyRequestReset.get()) {
            requestCount.set(0);
            hourlyRequestReset.set(now + 3600000);
            rateLimitExceeded = false;
        }

        // Controllo limite orario
        if (requestCount.get() >= MAX_REQUESTS_PER_HOUR) {
            rateLimitExceeded = true;
            rateLimitResetTime = hourlyRequestReset.get();
            return false;
        }

        // Controllo se in cooldown per rate limit
        if (rateLimitExceeded && now < rateLimitResetTime) {
            return false;
        }

        // Rate limiting globale con protezione burst
        synchronized (this) {
            long lastRequest = globalLastRequest.get();
            long timeSinceLastRequest = now - lastRequest;

            long requiredWait = rateLimitExceeded ? BURST_PROTECTION_MS : RATE_LIMIT_MS;

            if (timeSinceLastRequest < requiredWait) {
                try {
                    Thread.sleep(requiredWait - timeSinceLastRequest);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interruzione durante rate limiting", e);
                }
            }

            globalLastRequest.set(System.currentTimeMillis());
            requestCount.incrementAndGet();
        }

        return true;
    }

    private boolean isValidCityName(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            return false;
        }

        String trimmed = cityName.trim();

        // Controlli di sicurezza
        if (trimmed.length() > 100) { // Limite ragionevole
            return false;
        }

        // Consenti solo caratteri alfanumerici, spazi, trattini e apostrofi
        if (!trimmed.matches("^[a-zA-ZÀ-ÿ0-9\\s\\-'.,]+$")) {
            return false;
        }

        // Evita pattern sospetti
        if (trimmed.contains("..") || trimmed.contains("//") || trimmed.contains("\\")) {
            return false;
        }

        return true;
    }

    private CityData searchCitySync(String cityName) throws Exception {
        String encodedCity = URLEncoder.encode(cityName.trim(), StandardCharsets.UTF_8.toString());
        String urlString = NOMINATIM_URL + "?q=" + encodedCity +
                "&format=json&limit=1&addressdetails=1&accept-language=en";

        URL url = new URL(urlString);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();

            // Configura la connessione con sicurezza
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(false); // Sicurezza contro redirect attacks

            // Controlla il codice di risposta con gestione completa
            int responseCode = connection.getResponseCode();
            handleHttpResponseCode(responseCode, cityName);

            // Legge la risposta
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int totalLength = 0;
                final int MAX_RESPONSE_LENGTH = 50000; // Limite per evitare DoS

                while ((line = reader.readLine()) != null) {
                    totalLength += line.length();
                    if (totalLength > MAX_RESPONSE_LENGTH) {
                        throw new RuntimeException("Risposta API troppo grande, possibile attacco");
                    }
                    response.append(line);
                }
            }

            // Parsing JSON con validazione
            return parseNominatimResponse(response.toString(), cityName);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void handleHttpResponseCode(int responseCode, String cityName) {
        switch (responseCode) {
            case 200:
                // Reset rate limit exceeded flag on successful request
                rateLimitExceeded = false;
                break;
            case 429:
                rateLimitExceeded = true;
                rateLimitResetTime = System.currentTimeMillis() + BURST_PROTECTION_MS;
                throw new RuntimeException("Rate limit API superato. Riprova tra qualche minuto.");
            case 403:
                logger.severe("Accesso negato all'API Nominatim - possibile ban IP");
                throw new RuntimeException("Accesso negato all'API. Contatta l'amministratore.");
            case 404:
                throw new RuntimeException("Città non trovata: " + cityName);
            case 500:
            case 502:
            case 503:
                throw new RuntimeException("Servizio temporaneamente non disponibile. Riprova più tardi.");
            case 408: // Timeout
                throw new RuntimeException("Timeout della richiesta API. Riprova.");
            default:
                logger.warning("Codice di risposta HTTP inaspettato: " + responseCode + " per città: " + cityName);
                throw new RuntimeException("Errore API - Codice: " + responseCode);
        }
    }

    private CityData parseNominatimResponse(String jsonResponse, String originalCityName) {
        try {
            // Validazione JSON
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                throw new RuntimeException("Risposta API vuota");
            }

            JsonArray results = JsonParser.parseString(jsonResponse).getAsJsonArray();

            if (results.size() == 0) {
                throw new RuntimeException("Città non trovata: " + originalCityName);
            }

            JsonObject firstResult = results.get(0).getAsJsonObject();

            // Validazione coordinate con controlli di sicurezza
            if (!firstResult.has("lat") || !firstResult.has("lon")) {
                throw new RuntimeException("Coordinate mancanti nella risposta API");
            }

            double lat = firstResult.get("lat").getAsDouble();
            double lon = firstResult.get("lon").getAsDouble();

            // Validazione coordinate completa
            if (!isValidCoordinate(lat, lon)) {
                logger.warning("Coordinate non valide ricevute dall'API: lat=" + lat + ", lon=" + lon);
                throw new RuntimeException("Coordinate non valide ricevute dall'API");
            }

            String displayName = firstResult.has("display_name") ?
                    firstResult.get("display_name").getAsString() : originalCityName;

            // Estrae il nome della città dal display_name o address
            String cityName = extractCityName(firstResult, originalCityName);

            // Sanitizza il nome della città
            cityName = sanitizeCityName(cityName);

            return new CityData(cityName, lat, lon);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Errore nel parsing della risposta JSON per: " + originalCityName, e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Errore nel parsing della risposta JSON: " + e.getMessage(), e);
        }
    }

    private boolean isValidCoordinate(double lat, double lon) {
        // Controllo NaN e Infinity
        if (Double.isNaN(lat) || Double.isNaN(lon) ||
                Double.isInfinite(lat) || Double.isInfinite(lon)) {
            return false;
        }

        // Controllo range valido
        return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
    }

    private String sanitizeCityName(String cityName) {
        if (cityName == null) {
            return "Unknown";
        }

        // Rimuove caratteri potenzialmente pericolosi
        String sanitized = cityName.trim()
                .replaceAll("[<>\"'&]", "") // Rimuove caratteri HTML/XML
                .replaceAll("[\\p{Cntrl}]", ""); // Rimuove caratteri di controllo

        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return sanitized.isEmpty() ? "Unknown" : sanitized;
    }

    private String extractCityName(JsonObject result, String originalName) {
        try {
            // Prova a estrarre dalla sezione address se disponibile
            if (result.has("address")) {
                JsonObject address = result.getAsJsonObject("address");

                // Ordine di preferenza per il nome della città
                String[] cityFields = {"city", "town", "village", "municipality", "county"};

                for (String field : cityFields) {
                    if (address.has(field)) {
                        String cityName = address.get(field).getAsString();
                        if (cityName != null && !cityName.trim().isEmpty()) {
                            return cityName;
                        }
                    }
                }
            }

            // Fallback: estrae il primo elemento dal display_name
            if (result.has("display_name")) {
                String displayName = result.get("display_name").getAsString();
                if (displayName != null && displayName.contains(",")) {
                    String[] parts = displayName.split(",");
                    if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                        return parts[0].trim();
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Errore nell'estrazione nome città", e);
        }

        return originalName; // Fallback al nome originale
    }

    /**
     * Ottiene statistiche di utilizzo del servizio
     */
    public String getUsageStats() {
        long now = System.currentTimeMillis();
        long timeToReset = (hourlyRequestReset.get() - now) / 1000 / 60; // minuti

        return String.format("Richieste orarie: %d/%d, Reset in: %d min",
                requestCount.get(), MAX_REQUESTS_PER_HOUR, Math.max(0, timeToReset));
    }

    /**
     * Forza reset del rate limiting (solo per admin)
     */
    public void resetRateLimit() {
        rateLimitExceeded = false;
        rateLimitResetTime = 0;
        requestCount.set(0);
        hourlyRequestReset.set(System.currentTimeMillis() + 3600000);
        logger.info("Rate limit resettato manualmente");
    }
}