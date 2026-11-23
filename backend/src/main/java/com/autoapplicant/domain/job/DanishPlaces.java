package com.autoapplicant.domain.job;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Places the free-text location a job board gives ("Viby J", "København Ø og mulighed for
 * hjemmearbejde", "Koldingvej 12, 8260 Viby J") on the map.
 *
 * <p>Danish boards name postal districts far more often than kommuner, so the gazetteer is the
 * GeoNames postal-code list ({@code geo/dk-postal-places.tsv}): every postcode, its place name
 * and its municipality, with each municipality placed at the mean of its postcodes. Matching is
 * on whole words after folding æ/ø/å, so a street named after a town is not that town.
 *
 * <p>Empty means the posting can be anywhere: no known place is named, or it offers remote or
 * hybrid work. Callers treat that as in range, because a wasted call costs less than a missed job.
 */
public final class DanishPlaces {

    private static final String RESOURCE = "/geo/dk-postal-places.tsv";
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> ANYWHERE_WORDS = Set.of(
            "hjemmearbejde", "hjemmefra", "hjemmekontor", "fjernarbejde", "remote", "hybrid");
    private static final int COLUMNS = 5;
    private static final List<String> REMOTE_PHRASES = List.of(
            "remote", "fjernarbejde", "hjemmearbejde", "arbejde hjemmefra", "hybrid",
            "delvist hjemme", "delvist hjemmefra");

    /** Folded name or postcode to every point it names; a few place names exist twice. */
    private final Map<String, List<GeoPoint>> byKey;
    private final int longestKeyWords;

    private DanishPlaces(Map<String, List<GeoPoint>> byKey) {
        this.byKey = byKey;
        this.longestKeyWords = byKey.keySet().stream()
                .mapToInt(k -> k.split(" ").length).max().orElse(1);
    }

    /**
     * Whether a posting's own text offers remote or hybrid work. Job boards rarely say so in
     * the location field, and the crawl does not set a remote type, so the title and the
     * description are read too. Whole words and phrases only: "fjernvarme" is not remote work.
     */
    public static boolean mentionsRemoteWork(String text) {
        if (text == null) {
            return false;
        }
        String words = " " + fold(text) + " ";
        return REMOTE_PHRASES.stream().anyMatch(phrase -> words.contains(" " + phrase + " "));
    }

    /**
     * Where a posting is: anywhere when its title or description offers remote or hybrid work,
     * else the places its location names.
     */
    public List<GeoPoint> placesOfPosting(String location, String title, String description) {
        if (mentionsRemoteWork(title) || mentionsRemoteWork(description)) {
            return List.of();
        }
        return placesIn(location);
    }

    /** The bundled gazetteer, loaded once. */
    public static DanishPlaces shared() {
        return Shared.INSTANCE;
    }

    private static final class Shared {
        private static final DanishPlaces INSTANCE = load();
    }

    /** Loads the bundled gazetteer. */
    public static DanishPlaces load() {
        try (InputStream in = DanishPlaces.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing gazetteer " + RESOURCE);
            }
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DanishPlaces parse(BufferedReader reader) throws IOException {
        Map<String, List<GeoPoint>> byKey = new HashMap<>();
        Map<String, List<GeoPoint>> byMunicipality = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            addRow(line, byKey, byMunicipality);
        }
        addMunicipalityCentres(byKey, byMunicipality);
        return new DanishPlaces(byKey);
    }

    /** A postcode row: keyed by its postcode and its place name, and counted to its kommune. */
    private static void addRow(String line, Map<String, List<GeoPoint>> byKey,
                               Map<String, List<GeoPoint>> byMunicipality) {
        String[] cols = line.split("	");
        if (line.startsWith("#") || cols.length < COLUMNS) {
            return;
        }
        GeoPoint point = new GeoPoint(Double.parseDouble(cols[3]), Double.parseDouble(cols[4]));
        add(byKey, cols[0], point);
        add(byKey, fold(cols[1]), point);
        add(byMunicipality, municipalityName(cols[2]), point);
    }

    /** Each kommune is placed at the mean of its postcodes. */
    private static void addMunicipalityCentres(Map<String, List<GeoPoint>> byKey,
                                               Map<String, List<GeoPoint>> byMunicipality) {
        byMunicipality.forEach((name, points) -> {
            GeoPoint centre = mean(points);
            add(byKey, name, centre);
            // The capital's kommune is named in the genitive ("Københavns Kommune").
            if (name.endsWith("koebenhavns")) {
                add(byKey, name.substring(0, name.length() - 1), centre);
            }
        });
    }

    /**
     * The points a location names, or empty when it can be anywhere (remote, hybrid, or no
     * known place).
     */
    public List<GeoPoint> placesIn(String location) {
        if (location == null) {
            return List.of();
        }
        String[] words = fold(location).split(" ");
        return offersWorkFromAnywhere(words) ? List.of() : namedPlaces(words);
    }

    private static boolean offersWorkFromAnywhere(String[] words) {
        for (String word : words) {
            if (ANYWHERE_WORDS.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Every run of consecutive whole words that names a place, up to the longest place name. */
    private List<GeoPoint> namedPlaces(String[] words) {
        Set<GeoPoint> points = new LinkedHashSet<>();
        for (int start = 0; start < words.length; start++) {
            StringBuilder key = new StringBuilder(words[start]);
            points.addAll(byKey.getOrDefault(key.toString(), List.of()));
            for (int end = start + 1; end < Math.min(words.length, start + longestKeyWords); end++) {
                key.append(' ').append(words[end]);
                points.addAll(byKey.getOrDefault(key.toString(), List.of()));
            }
        }
        return List.copyOf(points);
    }

    /**
     * Where a user's home is: a postcode, a place name or a kommune, as typed in Settings. A
     * postcode wins over a name when both are given.
     */
    public Optional<GeoPoint> locate(String home) {
        if (home == null || home.isBlank()) {
            return Optional.empty();
        }
        for (String word : fold(home).split(" ")) {
            List<GeoPoint> byPostcode = byKey.get(word);
            if (word.chars().allMatch(Character::isDigit) && byPostcode != null) {
                return Optional.of(byPostcode.get(0));
            }
        }
        List<GeoPoint> points = placesIn(home);
        return points.isEmpty() ? Optional.empty() : Optional.of(points.get(0));
    }

    private static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT)
                .replace("æ", "ae").replace("ø", "oe").replace("å", "aa")
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "u").replace("é", "e");
        return NON_WORD.matcher(lower).replaceAll(" ").trim();
    }

    private static String municipalityName(String raw) {
        return fold(raw.replace(" Kommune", "").replace(" Municipality", ""));
    }

    private static void add(Map<String, List<GeoPoint>> map, String key, GeoPoint point) {
        if (!key.isEmpty()) {
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(point);
        }
    }

    private static GeoPoint mean(List<GeoPoint> points) {
        double lat = points.stream().mapToDouble(GeoPoint::lat).average().orElse(0);
        double lon = points.stream().mapToDouble(GeoPoint::lon).average().orElse(0);
        return new GeoPoint(lat, lon);
    }
}
