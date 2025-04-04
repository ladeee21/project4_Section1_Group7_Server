package com.example.group7fileflixserver;

import java.sql.Connection;

import static org.junit.Assert.*;

public class DatabaseTest {

    @org.junit.Test
    public void testRegisterUser_Success() throws Exception {
        String username = "user_test_1";
        String password = "pass123";

        // First ensuring user isn't already in the DB
        try (Connection conn = java.sql.DriverManager.getConnection(Database.URL);
             java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }

        boolean result = Database.registerUser(username, password);
        assertTrue("User should be registered successfully", result);
    }

    @org.junit.Test
    public void testAuthenticateUser_ValidCredentials() throws Exception {
        String username = "auth_test_user";
        String password = "securepass";

        // Clean slate
        try (Connection conn = java.sql.DriverManager.getConnection(Database.URL);
             java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }

        Database.registerUser(username, password);
        boolean isAuthenticated = Database.authenticateUser(username, password);
        assertTrue("Authentication should succeed with correct credentials", isAuthenticated);
    }

    @org.junit.Test
    public void testAuthenticateUser_InvalidPassword() throws Exception {
        String username = "bad_pass_user";
        String password = "rightpass";
        String wrongPassword = "wrongpass";

        // Reset user
        try (Connection conn = java.sql.DriverManager.getConnection(Database.URL);
             java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }

        Database.registerUser(username, password);
        boolean isAuthenticated = Database.authenticateUser(username, wrongPassword);
        assertFalse("Authentication should fail with wrong password", isAuthenticated);
    }


    @org.junit.Test
    public void testSaveFileEntry() throws Exception {
        String username = "file_saver_user";
        String password = "filepass";
        String filename = "example.txt";
        long size = 2048L;

        // Register and save file
        try (Connection conn = java.sql.DriverManager.getConnection(Database.URL);
             java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }

        Database.registerUser(username, password);
        Database.saveFile(username, filename, size);

        try (Connection conn = java.sql.DriverManager.getConnection(Database.URL);
             java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT * FROM files WHERE username = ? AND filename = ?")) {
            stmt.setString(1, username);
            stmt.setString(2, filename);
            java.sql.ResultSet rs = stmt.executeQuery();
            assertTrue("File record should exist in DB", rs.next());
            assertEquals("Stored file size should match", size, rs.getLong("size"));
        }
    }

    @org.junit.Test
    public void testClearFilesTable() throws Exception {
        String username = "deleteme";
        String password = "irrelevant";
        String filename = "tempfile.dat";
        long size = 1234;

        // Create record
        Database.registerUser(username, password);
        Database.saveFile(username, filename, size);

        Database.clearFilesTable();

        try (Connection conn = java.sql.DriverManager.getConnection(Database.URL);
             java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM files")) {
            java.sql.ResultSet rs = stmt.executeQuery();
            assertTrue("ResultSet should have a result", rs.next());
            assertEquals("Files table should be empty", 0, rs.getInt(1));
        }
    }
}