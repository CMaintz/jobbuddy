package com.autoapplicant.domain.job;

/** A circle on the map: everything within {@code radiusKm} of {@code centre}, straight line. */
public record GeoArea(GeoPoint centre, int radiusKm) {
}
