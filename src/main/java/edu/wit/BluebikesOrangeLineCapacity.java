package edu.wit;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BluebikesOrangeLineCapacity {

    private static final String STATION_INFO_URL =
            "https://gbfs.lyft.com/gbfs/1.1/bos/en/station_information.json";
    private static final String STATION_STATUS_URL =
            "https://gbfs.lyft.com/gbfs/1.1/bos/en/station_status.json";

    // 4 hours, polling every 5 minutes → 48 iterations
    private static final int NUM_ITERATIONS = 240;
    private static final long POLL_INTERVAL_MS = 1L * 60L * 1000L; // 1 minute

    private static final double RADIUS_METERS = 500.0;

    static class StationPoint {
        final String name;
        final double lat;
        final double lon;
        StationPoint(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    // SOUTH ORANGE LINE REGION (Forest Hills → Ruggles)
    private static final StationPoint[] SOUTH_ORANGE = {
            new StationPoint("Forest Hills", 42.2988, -71.1131),
            new StationPoint("Green Street", 42.3106, -71.1074),
            new StationPoint("Stony Brook", 42.3173, -71.1041),
            new StationPoint("Jackson Square", 42.3233, -71.0996),
            new StationPoint("Roxbury Crossing", 42.3313, -71.0956),
            new StationPoint("Ruggles", 42.3364, -71.0892)
    };

    // NORTH ORANGE LINE REGION (Malden → Sullivan)
    private static final StationPoint[] NORTH_ORANGE = {
            new StationPoint("Malden Center", 42.4267, -71.0744),
            new StationPoint("Wellington", 42.4024, -71.0771),
            new StationPoint("Assembly", 42.3927, -71.0772),
            new StationPoint("Sullivan", 42.3840, -71.0770)
    };

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        try (PrintWriter csv = new PrintWriter(new FileWriter("bluebikes_orange_capacity.csv", true))) {

            // CSV header (only if you want to avoid duplicates, you could check file size first)
            csv.println("timestamp,iteration,south_bikes,south_capacity,north_bikes,north_capacity");

            // ---- Load station info once ----
            String stationInfoJson = fetchUrl(STATION_INFO_URL);
            Map<String, double[]> stationCoords = parseStationCoordinates(stationInfoJson);
            if (stationCoords.isEmpty()) {
                System.err.println("Could not load station_information.json");
                return;
            }

            // ---- Assign stations to segments once ----
            Set<String> southIds = new HashSet<>();
            Set<String> northIds = new HashSet<>();
            assignStationsToSegments(stationCoords, southIds, northIds);

            System.out.println("South segment station IDs: " + southIds);
            System.out.println("North segment station IDs: " + northIds);

            // ---- Poll station_status every 5 minutes ----
            for (int i = 1; i <= NUM_ITERATIONS; i++) {
                long start = System.currentTimeMillis();
                String timestamp = TS.format(Instant.ofEpochMilli(start));

                int southBikes = 0, southCapacity = 0;
                int northBikes = 0, northCapacity = 0;

                try {
                    String statusJson = fetchUrl(STATION_STATUS_URL);
                    JsonNode statusRoot = new ObjectMapper().readTree(statusJson);
                    JsonNode stations = statusRoot.path("data").path("stations");

                    for (JsonNode st : stations) {
                        String id = st.path("station_id").asText();
                        int bikes = st.path("num_bikes_available").asInt();
                        int docks = st.path("num_docks_available").asInt();
                        int capacity = bikes + docks;

                        if (southIds.contains(id)) {
                            southBikes += bikes;
                            southCapacity += capacity;
                        }
                        if (northIds.contains(id)) {
                            northBikes += bikes;
                            northCapacity += capacity;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error during iteration " + i + ": " + e.getMessage());
                }

                // ---- CSV row ----
                csv.printf("%s,%d,%d,%d,%d,%d%n",
                        timestamp, i,
                        southBikes, southCapacity,
                        northBikes, northCapacity
                );
                csv.flush();

                // ---- Console summary ----
                System.out.println("==============================================");
                System.out.println("Iteration " + i + " @ " + timestamp);
                System.out.println("SOUTH (Forest Hills → Ruggles)");
                System.out.println("  Available Bikes: " + southBikes);
                System.out.println("  Total Capacity:  " + southCapacity);
                System.out.println("NORTH (Malden → Sullivan)");
                System.out.println("  Available Bikes: " + northBikes);
                System.out.println("  Total Capacity:  " + northCapacity);
                System.out.println("==============================================");

                if (i < NUM_ITERATIONS) {
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            System.out.println("Finished 4-hour Bluebikes capacity collection.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------- Helpers --------

    private static String fetchUrl(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        if (conn.getResponseCode() != 200)
            throw new IOException("HTTP " + conn.getResponseCode() + " for " + urlStr);

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        conn.disconnect();
        return sb.toString();
    }

    // station_id → [lat, lon]
    private static Map<String, double[]> parseStationCoordinates(String json) {
        Map<String, double[]> map = new HashMap<>();
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode stations = root.path("data").path("stations");
            for (JsonNode st : stations) {
                String id = st.path("station_id").asText();
                double lat = st.path("lat").asDouble();
                double lon = st.path("lon").asDouble();
                map.put(id, new double[]{lat, lon});
            }
        } catch (Exception e) {
            System.err.println("Parse error: " + e.getMessage());
        }
        return map;
    }

    private static void assignStationsToSegments(
            Map<String, double[]> coords,
            Set<String> south, Set<String> north
    ) {
        for (Map.Entry<String, double[]> e : coords.entrySet()) {
            String id = e.getKey();
            double lat = e.getValue()[0];
            double lon = e.getValue()[1];

            for (StationPoint p : SOUTH_ORANGE) {
                if (distanceMeters(lat, lon, p.lat, p.lon) <= RADIUS_METERS) {
                    south.add(id);
                    break;
                }
            }
            for (StationPoint p : NORTH_ORANGE) {
                if (distanceMeters(lat, lon, p.lat, p.lon) <= RADIUS_METERS) {
                    north.add(id);
                    break;
                }
            }
        }
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
