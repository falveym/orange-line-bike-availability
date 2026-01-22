# Orange Line & Bluebikes Segment Tracker

This project tracks MBTA Orange Line transit headways and Bluebikes availability across two key segments in Boston. It combines real-time GTFS data for the Orange Line with GBFS bikeshare data to monitor public transit reliability and bike availability in the surrounding areas.

---

## Features

1. **MBTA Orange Line Segment Tracking**

   * Monitors two segments:

     * **North Segment:** Malden to Sullivan
     * **South Segment:** Forest Hills to Ruggles
   * Polls MBTA Trip Updates feed (`TripUpdates.pb`) at regular intervals.
   * Computes:

     * Predicted train arrivals
     * Headways (time between trains)
     * Standard deviation of headways
   * Saves results to `orange_segment_headways.csv`.

2. **Bluebikes Orange Line Capacity**

   * Monitors bikeshare availability around stations near the same segments.
   * Polls GBFS endpoints (`station_information.json` & `station_status.json`).
   * Aggregates:

     * Number of bikes available
     * Total station capacity
   * Saves results to `bluebikes_orange_capacity.csv`.

---

## Prerequisites

* Java 11 or higher
* Maven or Gradle for dependency management
* Libraries:

  * `com.google.transit.realtime` (for GTFS Realtime)
  * `com.fasterxml.jackson.databind` (for GBFS JSON parsing)

---

## Setup & Usage

1. **Clone the repository**

```bash
git clone https://github.com/<your-username>/orange-line-bike-availability.git
cd orange-line-bike-availability
```

2. **Compile the Java programs**

```bash
javac -cp ".:lib/*" edu/wit/SegmentHeadwayTracker.java
javac -cp ".:lib/*" edu/wit/BluebikesOrangeLineCapacity.java
```

3. **Run the programs**

```bash
# Track Orange Line headways
java -cp ".:lib/*" edu.wit.SegmentHeadwayTracker

# Track Bluebikes availability
java -cp ".:lib/*" edu.wit.BluebikesOrangeLineCapacity
```

> Both programs will continuously poll the respective feeds and append data to CSV files.

---

## Output

* `orange_segment_headways.csv`
  | timestamp | iteration | sullivan_arrivals | sullivan_hw | sullivan_std | ruggles_arrivals | ruggles_hw | ruggles_std |

* `bluebikes_orange_capacity.csv`
  | timestamp | iteration | south_bikes | south_capacity | north_bikes | north_capacity |


---

## Notes

* Polling intervals and iteration counts are configurable via constants in the code.
* Stations are matched to segments using a 500-meter radius.
* Error handling is included for failed HTTP requests and missing data.

---
