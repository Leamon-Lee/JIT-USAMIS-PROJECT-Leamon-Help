package com.usamis.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * WHY bcrypt: Plain MD5/SHA hashes are rainbow-table vulnerable.
 * BCrypt adds a salt and work factor (cost), making brute-force
 * computationally expensive — even with GPU acceleration.
 *
 * Cost factor 12 → ~300ms per hash on modern hardware.
 * Suitable for login (rare operation); never use for bulk data.
 */
public final class PasswordUtil {

    private static final int COST = 12; // Increase to 13-14 for higher security machines

    private PasswordUtil() {}

    /** Hash a plaintext password. Store the result in DB. */
    public static String hash(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(COST));
    }

    /**
     * Verify a login attempt.
     * BCrypt.checkpw does a constant-time comparison —
     * prevents timing attacks that could leak hash info.
     */
    public static boolean verify(String plaintext, String storedHash) {
        if (plaintext == null || storedHash == null) return false;
        try {
            return BCrypt.checkpw(plaintext, storedHash);
        } catch (IllegalArgumentException e) {
            // Invalid hash format — never grant access
            return false;
        }
    }

    /** Validate password strength before hashing */
    public static boolean isStrong(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasUpper && hasDigit;
    }
}
