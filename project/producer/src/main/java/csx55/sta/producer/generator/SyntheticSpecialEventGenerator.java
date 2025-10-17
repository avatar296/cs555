package csx55.sta.producer.generator;

import csx55.sta.schema.EventType;
import csx55.sta.schema.SpecialEvent;

import java.util.concurrent.TimeUnit;

/**
 * Generates synthetic special events (concerts, sports, etc.) with realistic patterns.
 */
public class SyntheticSpecialEventGenerator extends AbstractSyntheticGenerator<SpecialEvent> {

    // Famous NYC venues and their typical zones
    private static final VenueInfo[] VENUES = {
            new VenueInfo(186, "Madison Square Garden", EventType.SPORTS, 15000, 25000),
            new VenueInfo(186, "Madison Square Garden", EventType.CONCERT, 18000, 20000),
            new VenueInfo(132, "Yankee Stadium", EventType.SPORTS, 45000, 55000),
            new VenueInfo(125, "Citi Field", EventType.SPORTS, 38000, 45000),
            new VenueInfo(162, "Lincoln Center", EventType.THEATER, 2000, 5000),
            new VenueInfo(164, "Barclays Center", EventType.SPORTS, 17000, 19000),
            new VenueInfo(164, "Barclays Center", EventType.CONCERT, 15000, 19000),
            new VenueInfo(162, "Carnegie Hall", EventType.CONCERT, 2800, 2800),
            new VenueInfo(237, "Javits Center", EventType.CONFERENCE, 5000, 40000),
            new VenueInfo(43, "Central Park", EventType.FESTIVAL, 10000, 50000)
    };

    // Event type weights when not using venue
    private static final double[] EVENT_TYPE_WEIGHTS = {
            0.30,  // CONCERT
            0.25,  // SPORTS
            0.20,  // CONFERENCE
            0.15,  // FESTIVAL
            0.05,  // THEATER
            0.05   // OTHER
    };

    private static class VenueInfo {
        final int zoneId;
        final String name;
        final EventType type;
        final int minAttendance;
        final int maxAttendance;

        VenueInfo(int zoneId, String name, EventType type, int minAttendance, int maxAttendance) {
            this.zoneId = zoneId;
            this.name = name;
            this.type = type;
            this.minAttendance = minAttendance;
            this.maxAttendance = maxAttendance;
        }
    }

    public SyntheticSpecialEventGenerator(boolean useRealtime, int timeProgressionSeconds) {
        super(useRealtime, timeProgressionSeconds);
    }

    /**
     * Generate a synthetic special event.
     */
    @Override
    public SpecialEvent generateEvent() {
        long timestamp = getNextTimestamp();

        // 70% chance to use a known venue, 30% random location
        boolean useVenue = random.nextDouble() < 0.7;

        if (useVenue) {
            return generateVenueEvent(timestamp);
        } else {
            return generateRandomEvent(timestamp);
        }
    }

    private SpecialEvent generateVenueEvent(long timestamp) {
        VenueInfo venue = VENUES[random.nextInt(VENUES.length)];

        int attendance = venue.minAttendance +
                random.nextInt(venue.maxAttendance - venue.minAttendance + 1);

        long startTime = timestamp + TimeUnit.HOURS.toMillis(random.nextInt(48)); // 0-48 hours from now
        long duration = generateEventDuration(venue.type);
        long endTime = startTime + duration;

        return SpecialEvent.newBuilder()
                .setTimestamp(timestamp)
                .setLocationId(venue.zoneId)
                .setEventType(venue.type)
                .setAttendanceEstimate(attendance)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setVenueName(venue.name)
                .build();
    }

    private SpecialEvent generateRandomEvent(long timestamp) {
        int locationId = generateLocationId();  // Use inherited method
        EventType eventType = generateEventType();
        int attendance = generateAttendance(eventType);

        long startTime = timestamp + TimeUnit.HOURS.toMillis(random.nextInt(48));
        long duration = generateEventDuration(eventType);
        long endTime = startTime + duration;

        return SpecialEvent.newBuilder()
                .setTimestamp(timestamp)
                .setLocationId(locationId)
                .setEventType(eventType)
                .setAttendanceEstimate(attendance)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setVenueName(null)
                .build();
    }

    private EventType generateEventType() {
        return selectWeighted(EventType.values(), EVENT_TYPE_WEIGHTS);
    }

    private int generateAttendance(EventType type) {
        int min, max;

        switch (type) {
            case SPORTS:
                min = 10000;
                max = 50000;
                break;
            case CONCERT:
                min = 5000;
                max = 20000;
                break;
            case CONFERENCE:
                min = 500;
                max = 10000;
                break;
            case FESTIVAL:
                min = 5000;
                max = 50000;
                break;
            case THEATER:
                min = 200;
                max = 3000;
                break;
            case OTHER:
            default:
                min = 100;
                max = 5000;
                break;
        }

        return min + random.nextInt(max - min + 1);
    }

    private long generateEventDuration(EventType type) {
        long baseHours;

        switch (type) {
            case SPORTS:
                baseHours = 3;  // ~3 hours
                break;
            case CONCERT:
                baseHours = 2;  // ~2-3 hours
                break;
            case CONFERENCE:
                baseHours = 8;  // Full day
                break;
            case FESTIVAL:
                baseHours = 6;  // Half day
                break;
            case THEATER:
                baseHours = 2;  // ~2 hours
                break;
            case OTHER:
            default:
                baseHours = 2;
                break;
        }

        // Add some variance (±50%)
        long variance = (long) (baseHours * 0.5 * random.nextDouble());
        if (random.nextBoolean()) {
            baseHours += variance;
        } else {
            baseHours = Math.max(1, baseHours - variance);
        }

        return TimeUnit.HOURS.toMillis(baseHours);
    }
}
