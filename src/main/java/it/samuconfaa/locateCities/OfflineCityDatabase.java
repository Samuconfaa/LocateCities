package it.samuconfaa.locateCities;

import java.util.HashMap;
import java.util.Map;

public class OfflineCityDatabase {

    private static final Map<String, CityData> OFFLINE_CITIES = new HashMap<>();

    static {
        // Città italiane principali
        addCity("roma", 41.9028, 12.4964);
        addCity("milano", 45.4642, 9.1900);
        addCity("napoli", 40.8518, 14.2681);
        addCity("torino", 45.0703, 7.6869);
        addCity("palermo", 38.1157, 13.3615);
        addCity("genova", 44.4056, 8.9463);
        addCity("bologna", 44.4949, 11.3426);
        addCity("firenze", 43.7696, 11.2558);
        addCity("bari", 41.1171, 16.8719);
        addCity("catania", 37.5079, 15.0830);
        addCity("venezia", 45.4408, 12.3155);
        addCity("verona", 45.4384, 10.9916);
        addCity("messina", 38.1938, 15.5540);
        addCity("padova", 45.4064, 11.8768);
        addCity("trieste", 45.6495, 13.7768);
        addCity("brescia", 45.5416, 10.2118);
        addCity("taranto", 40.4644, 17.2477);
        addCity("prato", 43.8777, 11.0955);
        addCity("parma", 44.8015, 10.3279);
        addCity("reggio calabria", 38.1138, 15.6619);

        // Città europee principali
        addCity("londra", 51.5074, -0.1278);
        addCity("parigi", 48.8566, 2.3522);
        addCity("madrid", 40.4168, -3.7038);
        addCity("berlino", 52.5200, 13.4050);
        addCity("amsterdam", 52.3676, 4.9041);
        addCity("bruxelles", 50.8503, 4.3517);
        addCity("vienna", 48.2082, 16.3738);
        addCity("praga", 50.0755, 14.4378);
        addCity("budapest", 47.4979, 19.0402);
        addCity("varsavia", 52.2297, 21.0122);
        addCity("stoccolma", 59.3293, 18.0686);
        addCity("oslo", 59.9139, 10.7522);

        // Città del mondo
        addCity("new york", 40.7128, -74.0060);
        addCity("los angeles", 34.0522, -118.2437);
        addCity("chicago", 41.8781, -87.6298);
        addCity("miami", 25.7617, -80.1918);
        addCity("toronto", 43.6532, -79.3832);
        addCity("vancouver", 49.2827, -123.1207);
        addCity("tokyo", 35.6762, 139.6503);
        addCity("osaka", 34.6937, 135.5023);
        addCity("seoul", 37.5665, 126.9780);
        addCity("shanghai", 31.2304, 121.4737);
        addCity("pechino", 39.9042, 116.4074);
        addCity("mumbai", 19.0760, 72.8777);
        addCity("delhi", 28.7041, 77.1025);
        addCity("sydney", -33.8688, 151.2093);
        addCity("melbourne", -37.8136, 144.9631);
        addCity("auckland", -36.8485, 174.7633);
        addCity("san paolo", -23.5505, -46.6333);
        addCity("rio de janeiro", -22.9068, -43.1729);
        addCity("buenos aires", -34.6118, -58.3960);
        addCity("città del messico", 19.4326, -99.1332);
        addCity("il cairo", 30.0444, 31.2357);
        addCity("dubai", 25.2048, 55.2708);
        addCity("istanbul", 41.0082, 28.9784);
    }

    private static void addCity(String name, double lat, double lon) {
        OFFLINE_CITIES.put(name.toLowerCase(), new CityData(name, lat, lon));
    }

    public static CityData findCity(String cityName) {
        return OFFLINE_CITIES.get(cityName.toLowerCase().trim());
    }

    public static boolean hasCity(String cityName) {
        return OFFLINE_CITIES.containsKey(cityName.toLowerCase().trim());
    }

    public static int getCityCount() {
        return OFFLINE_CITIES.size();
    }
}