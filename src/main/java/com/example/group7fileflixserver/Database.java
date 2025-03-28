package com.example.group7fileflixserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Database {

        private static final String URL = "jdbc:sqlite:fileflix.db";

    public static void initialize() {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL);";
            conn.createStatement().execute(createUsersTable);

            String createFilesTable = "CREATE TABLE IF NOT EXISTS files (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT NOT NULL, " +
                    "filename TEXT NOT NULL, " +
                    "FOREIGN KEY(username) REFERENCES users(username));";
            conn.createStatement().execute(createFilesTable);

            System.out.println("Database initialized successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static boolean registerUser(String username, String password) throws SQLException {
        // Insert the new user into the database
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
            System.out.println("New user registered: " + username);
            return true;
        } catch (SQLException e) {
            e.printStackTrace(); // Helps debug DB issues
            return false;
        }
    }

    // Method to check if a username already exists in the database
    public static boolean isUsernameTaken(String username) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String query = "SELECT COUNT(*) FROM users WHERE username = ?;";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.getInt(1) > 0; // Returns true if the username exists
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String query = "SELECT * FROM users WHERE username = ? AND password = ?;";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void saveFileRecord(String username, String filename, String absolutePath) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String query = "INSERT INTO files (username, filename) VALUES (?, ?);";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, filename);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
