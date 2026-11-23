package com.autoapplicant.domain.user;

/**
 * Where enrichment looks for postings: within {@code radiusKm} of {@code home}, straight line.
 *
 * <p>The default is 85 km around Viby J (8260). From there that takes in the towns about an
 * hour's drive away (Vejle 61 km, Fredericia 68, Herning 72, Kolding 82). It also lets in a few
 * places across water that take longer by road than the distance suggests (Samsø, north Fyn,
 * Kalundborg, Odsherred, central Odense at 82). Those are accepted: a wasted call costs less
 * than a missed job.
 *
 * @param home a postcode, place name or kommune as typed in Settings
 * @param radiusKm straight-line distance in kilometres, {@value #MIN_RADIUS_KM} to
 *     {@value #MAX_RADIUS_KM}
 */
public record EnrichmentArea(String home, Integer radiusKm) {

    public static final int MIN_RADIUS_KM = 5;
    public static final int MAX_RADIUS_KM = 400;
    public static final EnrichmentArea DEFAULT = new EnrichmentArea("8260 Viby J", 85);

    public boolean radiusInRange() {
        return radiusKm != null && radiusKm >= MIN_RADIUS_KM && radiusKm <= MAX_RADIUS_KM;
    }
}
