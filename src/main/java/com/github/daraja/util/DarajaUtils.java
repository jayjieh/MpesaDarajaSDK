package com.github.daraja.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public final class DarajaUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DarajaUtils() {}

    public static String getTimestamp() {
        return LocalDateTime.now().format(FORMATTER);
    }

    public static String generateStkPassword(String businessShortCode, String passkey, String timestamp) {
        String data = businessShortCode + passkey + timestamp;
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sanitizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or blank");
        }
        String clean = phone.replaceAll("[^0-9]", "");
        if (clean.startsWith("0")) {
            return "254" + clean.substring(1);
        } else if (clean.startsWith("7") || clean.startsWith("1")) {
            return "254" + clean;
        } else if (clean.startsWith("+254")) {
            return clean.substring(1);
        }
        return clean;
    }
}
