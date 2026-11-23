package com.autoapplicant.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Turning the free-text location a job board gives into places on the map. */
class DanishPlacesTest {

    private static final DanishPlaces PLACES = DanishPlaces.load();
    private static final GeoPoint VIBY_J = PLACES.locate("8260").orElseThrow();

    private static double kmFromVibyJ(GeoPoint p) {
        return GeoPoint.distanceKm(VIBY_J, p);
    }

    @Test
    void aPostalDistrictNameIsPlaced() {
        // Bug: only the 98 kommune names are known, so "Viby J", the most common way a Danish
        // job board names a place, resolves to nothing and every such posting counts as unknown.
        List<GeoPoint> points = PLACES.placesIn("Viby J");

        assertThat(points).isNotEmpty().allSatisfy(p -> assertThat(kmFromVibyJ(p)).isLessThan(5));
    }

    @Test
    void theOldAndNewSpellingOfAarhusAreTheSamePlace() {
        // Bug: "Århus" is not normalised to "Aarhus", so older postings are left unplaced.
        assertThat(PLACES.placesIn("Århus C")).isNotEmpty();
        assertThat(PLACES.placesIn("Århus C")).isEqualTo(PLACES.placesIn("Aarhus C"));
    }

    @Test
    void aStreetNamedAfterATownDoesNotPlaceThePostingInThatTown() {
        // Bug: substring matching reads "Koldingvej" as Kolding, 80 km away, and a posting next
        // door is judged by the wrong town.
        List<GeoPoint> points = PLACES.placesIn("Koldingvej 12, 8260 Viby J");

        assertThat(points).isNotEmpty().allSatisfy(p -> assertThat(kmFromVibyJ(p)).isLessThan(5));
    }

    @Test
    void aHyphenatedKommuneIsPlaced() {
        // Bug: the hyphen survives normalisation on one side only, so "Høje-Taastrup" matches
        // neither "Høje Taastrup" nor itself.
        assertThat(PLACES.placesIn("Høje-Taastrup")).isNotEmpty();
    }

    @Test
    void aHybridOrRemotePostingIsAnywhere() {
        // Bug: the town before "og mulighed for hjemmearbejde" decides alone, and a hybrid role
        // far away is skipped although she could take it.
        assertThat(PLACES.placesIn("Ballerup og mulighed for hjemmearbejde")).isEmpty();
        assertThat(PLACES.placesIn("Remote, Denmark")).isEmpty();
    }

    @Test
    void aPostingInSeveralTownsIsPlacedInAllOfThem() {
        // Bug: only the first town counts, so a role offered in Risskov too is judged by
        // Værløse alone.
        List<GeoPoint> points = PLACES.placesIn("Værløse, Vejle, Risskov");

        assertThat(points).anySatisfy(p -> assertThat(kmFromVibyJ(p)).isLessThan(15));
        assertThat(points).anySatisfy(p -> assertThat(kmFromVibyJ(p)).isGreaterThan(100));
    }

    @Test
    void anUnknownPlaceIsNotPlaced() {
        // Bug: text naming no place resolves to some unrelated match and the posting is judged
        // by it, rather than being let through as unknown.
        assertThat(PLACES.placesIn("Flere lokationer")).isEmpty();
        assertThat(PLACES.placesIn(null)).isEmpty();
    }

    @Test
    void aHomeCanBeGivenAsPostcodeOrPlaceName() {
        // Bug: Settings accepts "Viby J" but only a postcode resolves, so her home is rejected.
        assertThat(PLACES.locate("Viby J")).contains(VIBY_J);
        assertThat(PLACES.locate("8260 Viby J")).contains(VIBY_J);
        assertThat(PLACES.locate("Atlantis")).isEmpty();
    }

    @Test
    void remoteOrHybridWorkIsRecognisedInThePostingText() {
        // Bug: only the location field is read, and job boards rarely put "remote" there, so a
        // remote role far away is judged by its office town and skipped.
        assertThat(DanishPlaces.mentionsRemoteWork("Fuldt remote stilling")).isTrue();
        assertThat(DanishPlaces.mentionsRemoteWork("Du kan arbejde hjemmefra to dage om ugen")).isTrue();
        assertThat(DanishPlaces.mentionsRemoteWork("Mulighed for delvist hjemmearbejde")).isTrue();
        assertThat(DanishPlaces.mentionsRemoteWork("Vi arbejder hybrid")).isTrue();
        assertThat(DanishPlaces.mentionsRemoteWork("Fjernarbejde er muligt")).isTrue();
    }

    @Test
    void wordsThatOnlyLookLikeRemoteWorkAreNot() {
        // Bug: substring matching reads "fjernvarme" or "hybridbil" as remote work, so on-site
        // roles anywhere in Denmark pass the area filter.
        assertThat(DanishPlaces.mentionsRemoteWork("Kontor med fjernvarme og egen hybridbil")).isFalse();
        assertThat(DanishPlaces.mentionsRemoteWork("Fremmøde på kontoret i Esbjerg")).isFalse();
        assertThat(DanishPlaces.mentionsRemoteWork(null)).isFalse();
    }
}
