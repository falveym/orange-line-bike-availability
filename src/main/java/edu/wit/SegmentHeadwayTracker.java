package edu.wit;

import com.google.transit.realtime.GtfsRealtime;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SegmentHeadwayTracker {

    private static final String TRIP_UPDATES_URL = "https://cdn.mbta.com/realtime/TripUpdates.pb";

    // request total and frequency
    private static final int NUM_POLLS = 240;
    private static final long POLL_INTERVAL_MS = 1L * 60L * 1000L;

    // segment downstream stops
    private static final String SULLIVAN = "70030";     // North segment
    private static final String RUGGLES = "70010";      // South segment

    // global headway storage
    private static final List<Long> globalNorthHW = new ArrayList<>();
    private static final List<Long> globalSouthHW = new ArrayList<>();

    // time format
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) throws Exception {

        // csv file
        FileWriter fw = new FileWriter("orange_segment_headways.csv", true);
        fw.write("timestamp,iteration,sullivan_arrivals,sullivan_hw,sullivan_std,"
                + "ruggles_arrivals,ruggles_hw,ruggles_std\n");

        // iterate requests
        for (int i = 1; i <= NUM_POLLS; i++) {
            long start = System.currentTimeMillis(); // start time
            byte[] data = fetchURL(TRIP_UPDATES_URL); //TODO json data
            long latency = System.currentTimeMillis() - start; // latency

            if (data == null) {
                System.out.println("Poll " + i + ": FAILED download");
                continue;
            }

            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(data);

            // list of arrivals for each station
            List<Long> sullArrivals = new ArrayList<>();
            List<Long> ruggArrivals = new ArrayList<>();

            // extract predicted arrivals
            for (GtfsRealtime.FeedEntity e : feed.getEntityList()) {
                if (!e.hasTripUpdate()) continue;

                // iterate through each trip update
                for (GtfsRealtime.TripUpdate.StopTimeUpdate stu : e.getTripUpdate().getStopTimeUpdateList()) {
                    if (!stu.hasStopId() || !stu.hasArrival()) continue;

                    long arr = stu.getArrival().getTime();
                    String stop = stu.getStopId();
                    // collect sullivan and ruggles arrivals
                    if (stop.equals(SULLIVAN)) sullArrivals.add(arr);
                    if (stop.equals(RUGGLES)) ruggArrivals.add(arr);
                }
            }

            // compute headways
            List<Long> sullHW = computeHeadways(sullArrivals);
            List<Long> ruggHW = computeHeadways(ruggArrivals);

            // add to global lists
            globalNorthHW.addAll(sullHW);
            globalSouthHW.addAll(ruggHW);

            // calculate std dev
            double sullStd = stddev(sullHW);
            double ruggStd = stddev(ruggHW);

            // get time stamp
            String ts = TS.format(Instant.now());

            // write to CSV
            fw.write(String.format("%s,%d,\"%s\",\"%s\",%.2f,\"%s\",\"%s\",%.2f\n",
                    ts, i,
                    sullArrivals, sullHW, sullStd,
                    ruggArrivals, ruggHW, ruggStd
            ));

            // output summary to console
            System.out.println("------------------------------------------------");
            System.out.println("Poll " + i + " @ " + ts);
            System.out.println("Latency: " + latency + " ms");

            printSummary("North Segment (Sullivan)", sullArrivals, sullHW, sullStd);
            printSummary("South Segment (Ruggles)", ruggArrivals, ruggHW, ruggStd);

            Thread.sleep(POLL_INTERVAL_MS);
        }

        fw.close();

        // final summary
        System.out.println("\n================ FINAL SEGMENT HEADWAY SUMMARY ================");
        printFinal("North Segment (Malden to Sullivan)", globalNorthHW);
        printFinal("South Segment (Forest Hills to Ruggles)", globalSouthHW);
        System.out.println("===============================================================\n");
    }

    // fetches json file from url
    private static byte[] fetchURL(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);

            if (c.getResponseCode() != 200) return null;

            try (InputStream in = c.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);

                return out.toByteArray();
            }

        } catch (IOException e) {
            return null;
        }
    }

    // calculates scheduled headways in minutes
    private static List<Long> computeHeadways(List<Long> arrivals) {
        List<Long> hw = new ArrayList<>();
        if (arrivals.size() < 2) return hw;

        Collections.sort(arrivals);
        for (int i = 1; i < arrivals.size(); i++) {
            hw.add((arrivals.get(i) - arrivals.get(i - 1)) / 60);
        }
        return hw;
    }

    // standard deviation
    private static double stddev(List<Long> list) {
        if (list.size() < 2) return 0.0;
        double avg = list.stream().mapToDouble(a -> a).average().orElse(0);
        double sum = 0;
        for (double x : list) sum += (x - avg) * (x - avg);
        return Math.sqrt(sum / list.size());
    }

    // formating for iteration summary
    private static void printSummary(String name, List<Long> arrivals, List<Long> hw, double sd) {
        System.out.printf("%s: arrivals=%d  headways=%s  std=%.2f\n",
                name, arrivals.size(), hw.toString(), sd);
    }
    // formatting for final summary
    private static void printFinal(String name, List<Long> hw) {
        if (hw.isEmpty()) {
            System.out.println(name + ": NO DATA");
            return;
        }
        double avg = hw.stream().mapToDouble(a -> a).average().orElse(0);
        double sd = stddev(hw);
        System.out.printf("%s FINAL: samples=%d avg=%.2f min  std=%.2f\n",
                name, hw.size(), avg, sd);
    }
}
