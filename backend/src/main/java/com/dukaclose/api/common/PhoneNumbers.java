// PhoneNumbers.java
package com.dukaclose.api.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhoneNumbers {

    // 07xx / 01xx, 2547xx / 2541xx, +2547xx / +2541xx
    private static final Pattern KENYAN_MOBILE = Pattern.compile("^(?:\\+?254|0)([17]\\d{8})$");

    private PhoneNumbers() {}

    /** Returns +254XXXXXXXXX or throws a 400. Use when registering a number. */
    public static String normalise(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[\\s\\-()]", "");
        Matcher m = KENYAN_MOBILE.matcher(cleaned);
        if (!m.matches()) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid Kenyan phone number, e.g. 0712 345 678");
        }
        return "+254" + m.group(1);
    }

    /** Normalizes when possible, otherwise returns the trimmed input. Use at login so bad input just fails to match. */
    public static String lenient(String raw) {
        try {
            return normalise(raw);
        } catch (ApiException e) {
            return raw == null ? "" : raw.trim();
        }
    }
}