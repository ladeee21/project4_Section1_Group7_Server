package com.example.group7fileflixserver;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHashing {

    // Hash a plain text password
    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    // Verify if the provided password matches the stored hashed password
    public static boolean verifyPassword(String providedPassword, String storedHash) {
        return BCrypt.checkpw(providedPassword, storedHash);
    }
}
