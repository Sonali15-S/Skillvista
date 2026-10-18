package com.skillvista.web;

import java.util.Map;

/** Small helpers for reading values out of JSON request bodies. */
public final class Req {
    private Req() {}

    public static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString().trim();
    }

    public static int integer(Map<String, Object> body, String key, int fallback) {
        try { return Integer.parseInt(str(body, key)); } catch (NumberFormatException e) { return fallback; }
    }

    /** Reads a profile link, adds https:// if missing and checks it points at the expected site (e.g. linkedin.com). */
    public static String url(Map<String, Object> body, String key, String domain, String message) {
        String v = str(body, key);
        if (v.isEmpty()) throw new ApiException(400, message);
        if (!v.toLowerCase().startsWith("http://") && !v.toLowerCase().startsWith("https://")) v = "https://" + v;
        String host;
        try { host = java.net.URI.create(v).getHost(); } catch (Exception e) { host = null; }
        if (host == null || !(host.equalsIgnoreCase(domain) || host.toLowerCase().endsWith("." + domain.toLowerCase())))
            throw new ApiException(400, message);
        return v;
    }

    public static String require(Map<String, Object> body, String key, String message) {
        String v = str(body, key);
        if (v.isEmpty()) throw new ApiException(400, message);
        return v;
    }
}
