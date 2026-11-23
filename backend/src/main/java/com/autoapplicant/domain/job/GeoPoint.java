package com.autoapplicant.domain.job;

/** A point on the map in decimal degrees. */
public record GeoPoint(double lat, double lon) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Great-circle (haversine) distance in kilometres; straight line, not road distance. */
    public static double distanceKm(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat - a.lat);
        double dLon = Math.toRadians(b.lon - a.lon);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
