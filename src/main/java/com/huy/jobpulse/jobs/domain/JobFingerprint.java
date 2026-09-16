package com.huy.jobpulse.jobs.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class JobFingerprint {

    private JobFingerprint() {
    }

    public static String create(
            String title,
            String location,
            String description,
            String applyUrl
    ) {
        String canonicalValue = String.join(
                "\u001f",
                normalize(title),
                normalize(location),
                normalize(description),
                normalize(applyUrl)
        );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalValue.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}