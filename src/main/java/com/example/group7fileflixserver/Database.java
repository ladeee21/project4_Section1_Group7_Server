package com.example.group7fileflixserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.zip.Checksum;

// database for the fileflix application
public class Database {

        private static final String URL = "jdbc:sqlite:C:/Users/navje/project4_Section1_Group7_Server/fileflix.db";

    public static void initialize() {

        try (Connection conn = DriverManager.getConnection(URL)) {
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL);";
            conn.createStatement().execute(createUsersTable);

            //Creating File tables

            String createFilesTable = "CREATE TABLE IF NOT EXISTS files (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT NOT NULL, " +
                    "filename TEXT NOT NULL, " +
                    "FOREIGN KEY(username) REFERENCES users(username));";
            conn.createStatement().execute(createFilesTable);

            // Check if 'size' column exists, and if not, add it
            String checkColumnQuery = "PRAGMA table_info(files);";
            ResultSet rs = conn.createStatement().executeQuery(checkColumnQuery);
            boolean sizeColumnExists = false;

            while (rs.next()) {
                if (rs.getString("name").equals("size")) {
                    sizeColumnExists = true;
                    break;
                }
            }

            // If the 'size' column doesn't exist, add it
            if (!sizeColumnExists) {
                String alterTableQuery = "ALTER TABLE files ADD COLUMN size INT NOT NULL DEFAULT 0;";
                conn.createStatement().execute(alterTableQuery);
                System.out.println("Added 'size' column to the 'files' table.");
            }

            System.out.println("Database initialized successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Authenticating user
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



    public static void saveFile(String username, String filename, long fileSize) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String query = "INSERT INTO files (username, filename, size) VALUES (?, ?, ?);";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, filename);
            stmt.setLong(3, fileSize);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




}
