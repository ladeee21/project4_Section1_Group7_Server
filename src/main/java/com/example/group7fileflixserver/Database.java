package com.example.group7fileflixserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Database {

        private static final String URL = "jdbc:sqlite:fileflix.db";

        public static Connection connect() {
            try {
                return DriverManager.getConnection(URL);
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        }

        public static void createTables() {
            String usersTable = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT);";
            String filesTable = "CREATE TABLE IF NOT EXISTS files (id INTEGER PRIMARY KEY AUTOINCREMENT, filename TEXT, filepath TEXT, uploaded_by TEXT, FOREIGN KEY (uploaded_by) REFERENCES users(username));";
            String photosTable = "CREATE TABLE IF NOT EXISTS photos (id INTEGER PRIMARY KEY AUTOINCREMENT, photo_name TEXT, photo_path TEXT, uploaded_by TEXT, FOREIGN KEY (uploaded_by) REFERENCES users(username));";

            try (Connection conn = connect();
                 PreparedStatement stmt1 = conn.prepareStatement(usersTable);
                 PreparedStatement stmt2 = conn.prepareStatement(filesTable);
                 PreparedStatement stmt3 = conn.prepareStatement(photosTable))
            {

                 stmt1.execute();
                 stmt2.execute();
                 stmt3.execute();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public static boolean registerUser(String username, String password) {
            String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
            try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        public static boolean authenticateUser(String username, String password) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        public static boolean saveFileRecord(String filename, String filepath, String uploadedBy) {
            String sql = "INSERT INTO files (filename, filepath, uploaded_by) VALUES (?, ?, ?)";
            try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, filename);
                stmt.setString(2, filepath);
                stmt.setString(3, uploadedBy);
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

    public static boolean savePhotoRecord(String photoName, String photoPath, String uploadedBy) {
        String sql = "INSERT INTO photos (photo_name, photo_path, uploaded_by) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, photoName);
            stmt.setString(2, photoPath);
            stmt.setString(3, uploadedBy);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


}
