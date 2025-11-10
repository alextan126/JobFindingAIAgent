package com.example.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Utility class for password hashing and verification using BCrypt.
 */
public final class PasswordUtil {

    private static final int BCRYPT_COST = 12; // Cost factor for BCrypt (higher = more secure but slower)

    private PasswordUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Hash a plain text password using BCrypt.
     * @param plainPassword the plain text password
     * @return the BCrypt hashed password
     */
    public static String hashPassword(String plainPassword) {
        return BCrypt.withDefaults()
            .hashToString(BCRYPT_COST, plainPassword.toCharArray());
    }

    /**
     * Verify a plain text password against a BCrypt hash.
     * @param plainPassword the plain text password
     * @param hashedPassword the BCrypt hashed password
     * @return true if password matches, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        BCrypt.Result result = BCrypt.verifyer()
            .verify(plainPassword.toCharArray(), hashedPassword);
        return result.verified;
    }
}
