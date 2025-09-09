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

public class GeocodingService {

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "LocateCities-Minecraft-Plugin/1.0";

    private final int timeout;

    public GeocodingService(int timeout) {
        this.timeout = timeout;
    }

    public CompletableFuture<CityData> searchCity(String cityName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return searchCitySync(cityName);
            } catch (Exception e) {
                throw new RuntimeException("Errore nella ricerca della città: " + e.getMessage(), e);
            }
        });
    }

    private CityData searchCitySync(String cityName) throws Exception {
        String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8.toString());
        String urlString = NOMINATIM_URL + "?q=" + encodedCity + "&format=json&limit=1";

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // Configura la connessione
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            // Controlla il codice di risposta
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("API request failed with code: " + responseCode);
            }

            // Legge la risposta
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
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

            double lat = firstResult.get("lat").getAsDouble();
            double lon = firstResult.get("lon").getAsDouble();
            String displayName = firstResult.get("display_name").getAsString();

            // Estrae il nome della città dal display_name
            String cityName = extractCityName(displayName, originalCityName);

            return new CityData(cityName, lat, lon);

        } catch (Exception e) {
            throw new RuntimeException("Errore nel parsing della risposta JSON: " + e.getMessage(), e);
        }
    }

    private String extractCityName(String displayName, String originalName) {
        // Estrae il primo elemento del display_name (solitamente il nome della città)
        String[] parts = displayName.split(",");
        if (parts.length > 0) {
            return parts[0].trim();
        }
        return originalName; // Fallback al nome originale
    }
}