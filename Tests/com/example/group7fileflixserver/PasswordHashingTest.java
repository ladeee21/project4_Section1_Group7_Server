package com.example.group7fileflixserver;

import static org.junit.Assert.*;

public class PasswordHashingTest {

    @org.junit.Test
    public void hashPassword() {
        String plainPassword = "MySecret123";
        String hashed = PasswordHashing.hashPassword(plainPassword);
        assertNotNull("Hashed password should not be null", hashed);
        assertNotEquals("Hashed password should not equal plain password", plainPassword, hashed);
        assertTrue("Hash should match original password", PasswordHashing.verifyPassword(plainPassword, hashed));
    }

    @org.junit.Test
    public void verifyPassword() {
        String plainPassword = "TestPassword";
        String hashed = PasswordHashing.hashPassword(plainPassword);
        assertTrue("Correct password should return true", PasswordHashing.verifyPassword(plainPassword, hashed));
        assertFalse("Incorrect password should return false", PasswordHashing.verifyPassword("WrongPassword", hashed));
    }
}