package it.samuconfaa.locateCities;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;

public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "LocateCities-Minecraft-Plugin/1.0";
    private static final long RATE_LIMIT_MS = 1000; // 1 secondo tra richieste API

    private final int timeout;
    private final Map<String, Long> lastRequestTime = new HashMap<>();
    private long globalLastRequest = 0;

    public GeocodingService(int timeout) {
        this.timeout = timeout;
    }

    public CompletableFuture<CityData> searchCity(String cityName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Rate limiting globale per rispettare i termini di Nominatim
                synchronized (this) {
                    long now = System.currentTimeMillis();
                    if (globalLastRequest != 0 && (now - globalLastRequest) < RATE_LIMIT_MS) {
                        Thread.sleep(RATE_LIMIT_MS - (now - globalLastRequest));
                    }
                    globalLastRequest = System.currentTimeMillis();
                }

                return searchCitySync(cityName);
            } catch (Exception e) {
                throw new RuntimeException("Errore nella ricerca della città: " + e.getMessage(), e);
            }
        });
    }

    private CityData searchCitySync(String cityName) throws Exception {
        String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8.toString());
        String urlString = NOMINATIM_URL + "?q=" + encodedCity + "&format=json&limit=1&addressdetails=1";

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // Configura la connessione
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            // Controlla il codice di risposta
            int responseCode = connection.getResponseCode();

            switch (responseCode) {
                case 200:
                    break; // OK
                case 429:
                    throw new RuntimeException("Troppo richieste all'API. Riprova più tardi.");
                case 403:
                    throw new RuntimeException("Accesso negato all'API di geocoding.");
                case 500:
                    throw new RuntimeException("Errore interno del server di geocoding.");
                default:
                    throw new RuntimeException("API request failed with code: " + responseCode);
            }

            // Legge la risposta
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Parsing JSON
            return parseNominatimResponse(response.toString(), cityName);

        } finally {
            connection.disconnect();
        }
    }

    private CityData parseNominatimResponse(String jsonResponse, String originalCityName) {
        try {
            JsonArray results = JsonParser.parseString(jsonResponse).getAsJsonArray();

            if (results.size() == 0) {
                throw new RuntimeException("Città non trovata: " + originalCityName);
            }

            JsonObject firstResult = results.get(0).getAsJsonObject();

            // Validazione coordinate
            if (!firstResult.has("lat") || !firstResult.has("lon")) {
                throw new RuntimeException("Coordinate mancanti nella risposta API");
            }

            double lat = firstResult.get("lat").getAsDouble();
            double lon = firstResult.get("lon").getAsDouble();

            // Validazione coordinate
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                throw new RuntimeException("Coordinate non valide ricevute dall'API");
            }

            String displayName = firstResult.has("display_name") ?
                    firstResult.get("display_name").getAsString() : originalCityName;

            // Estrae il nome della città dal display_name o address
            String cityName = extractCityName(firstResult, originalCityName);

            return new CityData(cityName, lat, lon);

        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Errore nel parsing della risposta JSON: " + e.getMessage(), e);
        }
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
                        return address.get(field).getAsString();
                    }
                }
            }

            // Fallback: estrae il primo elemento dal display_name
            if (result.has("display_name")) {
                String displayName = result.get("display_name").getAsString();
                String[] parts = displayName.split(",");
                if (parts.length > 0) {
                    return parts[0].trim();
                }
            }

        } catch (Exception e) {
            // Se c'è un errore nell'estrazione, usa il nome originale
        }

        return originalName; // Fallback al nome originale
    }
}