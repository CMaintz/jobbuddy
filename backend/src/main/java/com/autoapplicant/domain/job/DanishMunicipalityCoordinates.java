package com.autoapplicant.domain.job;

import java.util.HashMap;
import java.util.Map;

/**
 * Approximate centroid coordinates for all 98 Danish municipalities (kommuner).
 * Used for haversine distance filtering in the matching engine.
 * Values are [latitude, longitude] in decimal degrees.
 */
public final class DanishMunicipalityCoordinates {

    private DanishMunicipalityCoordinates() {}

    // Key: lowercase municipality name (matches what job enrichment stores in Job.municipality)
    private static final Map<String, double[]> COORDS = new HashMap<>();

    static {
        // Capital Region (Hovedstaden)
        COORDS.put("københavn",         new double[]{55.6761, 12.5683});
        COORDS.put("copenhagen",        new double[]{55.6761, 12.5683});
        COORDS.put("frederiksberg",     new double[]{55.6786, 12.5318});
        COORDS.put("dragør",            new double[]{55.5930, 12.6683});
        COORDS.put("tårnby",            new double[]{55.5951, 12.5919});
        COORDS.put("taarnby",           new double[]{55.5951, 12.5919});
        COORDS.put("albertslund",       new double[]{55.6596, 12.3635});
        COORDS.put("ballerup",          new double[]{55.7307, 12.3543});
        COORDS.put("brøndby",           new double[]{55.6559, 12.4209});
        COORDS.put("brondby",           new double[]{55.6559, 12.4209});
        COORDS.put("gentofte",          new double[]{55.7486, 12.5493});
        COORDS.put("gladsaxe",          new double[]{55.7341, 12.4793});
        COORDS.put("glostrup",          new double[]{55.6648, 12.3978});
        COORDS.put("herlev",            new double[]{55.7271, 12.4384});
        COORDS.put("hvidovre",          new double[]{55.6561, 12.4753});
        COORDS.put("høje-taastrup",     new double[]{55.6627, 12.2736});
        COORDS.put("hoje-taastrup",     new double[]{55.6627, 12.2736});
        COORDS.put("høje taastrup",     new double[]{55.6627, 12.2736});
        COORDS.put("ishøj",             new double[]{55.6128, 12.3544});
        COORDS.put("ishoj",             new double[]{55.6128, 12.3544});
        COORDS.put("rødovre",           new double[]{55.6839, 12.4523});
        COORDS.put("rodovre",           new double[]{55.6839, 12.4523});
        COORDS.put("vallensbæk",        new double[]{55.6371, 12.3595});
        COORDS.put("vallensbek",        new double[]{55.6371, 12.3595});
        COORDS.put("furesø",            new double[]{55.7861, 12.3724});
        COORDS.put("fureso",            new double[]{55.7861, 12.3724});
        COORDS.put("egedal",            new double[]{55.7694, 12.2195});
        COORDS.put("rudersdal",         new double[]{55.8302, 12.4902});
        COORDS.put("lyngby-taarbæk",    new double[]{55.7694, 12.5027});
        COORDS.put("lyngby-taarbek",    new double[]{55.7694, 12.5027});
        COORDS.put("lyngby",            new double[]{55.7694, 12.5027});
        COORDS.put("hillerød",          new double[]{55.9307, 12.3046});
        COORDS.put("hillerod",          new double[]{55.9307, 12.3046});
        COORDS.put("frederikssund",     new double[]{55.8392, 12.0618});
        COORDS.put("halsnæs",           new double[]{55.9550, 11.9694});
        COORDS.put("halsnaes",          new double[]{55.9550, 11.9694});
        COORDS.put("gribskov",          new double[]{56.0650, 12.2987});
        COORDS.put("helsingør",         new double[]{56.0365, 12.6136});
        COORDS.put("helsingor",         new double[]{56.0365, 12.6136});
        COORDS.put("elsinore",          new double[]{56.0365, 12.6136});
        COORDS.put("allerød",           new double[]{55.8624, 12.3495});
        COORDS.put("allerod",           new double[]{55.8624, 12.3495});
        COORDS.put("fredensborg",       new double[]{55.9759, 12.4028});
        COORDS.put("hørsholm",          new double[]{55.8800, 12.5014});
        COORDS.put("horsholm",          new double[]{55.8800, 12.5014});
        COORDS.put("bornholm",          new double[]{55.1067, 14.9139});

        // Zealand (Sjælland)
        COORDS.put("roskilde",          new double[]{55.6415, 12.0803});
        COORDS.put("greve",             new double[]{55.5913, 12.2965});
        COORDS.put("solrød",            new double[]{55.5414, 12.2208});
        COORDS.put("solrod",            new double[]{55.5414, 12.2208});
        COORDS.put("køge",              new double[]{55.4574, 12.1845});
        COORDS.put("koge",              new double[]{55.4574, 12.1845});
        COORDS.put("lejre",             new double[]{55.6126, 12.0115});
        COORDS.put("stevns",            new double[]{55.3539, 12.2148});
        COORDS.put("ringsted",          new double[]{55.4453, 11.7889});
        COORDS.put("sorø",              new double[]{55.4378, 11.5570});
        COORDS.put("soro",              new double[]{55.4378, 11.5570});
        COORDS.put("holbæk",            new double[]{55.7173, 11.7144});
        COORDS.put("holbaek",           new double[]{55.7173, 11.7144});
        COORDS.put("odsherred",         new double[]{55.8853, 11.5888});
        COORDS.put("kalundborg",        new double[]{55.6686, 11.0879});
        COORDS.put("slagelse",          new double[]{55.4012, 11.3534});
        COORDS.put("næstved",           new double[]{55.2298, 11.7613});
        COORDS.put("naestved",          new double[]{55.2298, 11.7613});
        COORDS.put("faxe",              new double[]{55.2591, 12.1277});
        COORDS.put("vordingborg",       new double[]{55.0040, 11.9047});
        COORDS.put("guldborgsund",      new double[]{54.7699, 11.8716});
        COORDS.put("lolland",           new double[]{54.7619, 11.4897});
        COORDS.put("køge bugt",         new double[]{55.5913, 12.2965}); // maps to greve

        // South Denmark (Syddanmark)
        COORDS.put("odense",            new double[]{55.3959, 10.3883});
        COORDS.put("svendborg",         new double[]{55.0581, 10.6138});
        COORDS.put("nyborg",            new double[]{55.3086, 10.8016});
        COORDS.put("kerteminde",        new double[]{55.4506, 10.6636});
        COORDS.put("middelfart",        new double[]{55.4950, 9.7371});
        COORDS.put("assens",            new double[]{55.2701, 9.9042});
        COORDS.put("nordfyns",          new double[]{55.5097, 10.2143});
        COORDS.put("faaborg-midtfyn",   new double[]{55.0964, 10.2396});
        COORDS.put("langeland",         new double[]{54.8553, 10.7975});
        COORDS.put("ærø",               new double[]{54.8801, 10.4141});
        COORDS.put("aero",              new double[]{54.8801, 10.4141});
        COORDS.put("fredericia",        new double[]{55.5665, 9.7536});
        COORDS.put("vejle",             new double[]{55.7112, 9.5360});
        COORDS.put("kolding",           new double[]{55.4890, 9.4717});
        COORDS.put("billund",           new double[]{55.7294, 9.1140});
        COORDS.put("esbjerg",           new double[]{55.4667, 8.4500});
        COORDS.put("fanø",              new double[]{55.4240, 8.4127});
        COORDS.put("fano",              new double[]{55.4240, 8.4127});
        COORDS.put("varde",             new double[]{55.6225, 8.4787});
        COORDS.put("vejen",             new double[]{55.4867, 9.1429});
        COORDS.put("haderslev",         new double[]{55.2522, 9.4897});
        COORDS.put("sønderborg",        new double[]{54.9078, 9.7917});
        COORDS.put("sonderborg",        new double[]{54.9078, 9.7917});
        COORDS.put("åbenrå",            new double[]{55.0454, 9.4178});
        COORDS.put("aabenraa",          new double[]{55.0454, 9.4178});
        COORDS.put("tønder",            new double[]{54.9367, 8.8617});
        COORDS.put("tonder",            new double[]{54.9367, 8.8617});

        // Central Jutland (Midtjylland)
        COORDS.put("aarhus",            new double[]{56.1629, 10.2039});
        COORDS.put("århus",             new double[]{56.1629, 10.2039});
        COORDS.put("horsens",           new double[]{55.8583, 9.8452});
        COORDS.put("silkeborg",         new double[]{56.1681, 9.5504});
        COORDS.put("skanderborg",       new double[]{56.0488, 9.9272});
        COORDS.put("randers",           new double[]{56.4607, 10.0365});
        COORDS.put("norddjurs",         new double[]{56.4606, 10.6456});
        COORDS.put("syddjurs",          new double[]{56.2500, 10.5500});
        COORDS.put("favrskov",          new double[]{56.3722, 9.9419});
        COORDS.put("odder",             new double[]{55.9742, 10.1579});
        COORDS.put("hedensted",         new double[]{55.7725, 9.7003});
        COORDS.put("ikast-brande",      new double[]{56.1333, 9.1531});
        COORDS.put("herning",           new double[]{56.1396, 8.9730});
        COORDS.put("ringkøbing-skjern", new double[]{56.0903, 8.2419});
        COORDS.put("ringkobing-skjern", new double[]{56.0903, 8.2419});
        COORDS.put("ringkøbing",        new double[]{56.0903, 8.2419});
        COORDS.put("struer",            new double[]{56.4920, 8.5837});
        COORDS.put("lemvig",            new double[]{56.5459, 8.3084});
        COORDS.put("holstebro",         new double[]{56.3588, 8.6125});
        COORDS.put("skive",             new double[]{56.5671, 9.0316});
        COORDS.put("viborg",            new double[]{56.4531, 9.4017});

        // North Jutland (Nordjylland)
        COORDS.put("aalborg",           new double[]{57.0488, 9.9217});
        COORDS.put("ålborg",            new double[]{57.0488, 9.9217});
        COORDS.put("frederikshavn",     new double[]{57.4399, 10.5348});
        COORDS.put("hjørring",          new double[]{57.4607, 9.9840});
        COORDS.put("hjorring",          new double[]{57.4607, 9.9840});
        COORDS.put("brønderslev",       new double[]{57.2706, 9.9552});
        COORDS.put("bronderslev",       new double[]{57.2706, 9.9552});
        COORDS.put("jammerbugt",        new double[]{57.1626, 9.4996});
        COORDS.put("vesthimmerlands",   new double[]{56.7947, 9.6522});
        COORDS.put("rebild",            new double[]{56.7447, 9.7933});
        COORDS.put("mariagerfjord",     new double[]{56.6443, 10.0100});
        COORDS.put("morsø",             new double[]{56.7947, 8.7481});
        COORDS.put("morso",             new double[]{56.7947, 8.7481});
        COORDS.put("thisted",           new double[]{56.9538, 8.6923});
        COORDS.put("læsø",              new double[]{57.2614, 11.0000});
        COORDS.put("laeso",             new double[]{57.2614, 11.0000});
    }

    /**
     * Returns [lat, lng] for the given municipality name, or null if not found.
     * Lookup is case-insensitive and strips leading/trailing whitespace.
     */
    public static double[] get(String municipalityName) {
        if (municipalityName == null) return null;
        return COORDS.get(municipalityName.toLowerCase().trim());
    }

    /**
     * Computes the haversine great-circle distance in kilometres between two points.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
