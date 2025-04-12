package com.example.group7fileflixserver;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


// Database for the Fileflix application
public class Database {

    private static final String DB_PATH = Paths.get(System.getProperty("user.dir"), "fileflix.db").toString();
    public static final String URL = "jdbc:sqlite:" + DB_PATH;

    public static void initialize() {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL);";
            conn.createStatement().execute(createUsersTable);

            // Creating File tables
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

    public static void clearFilesTable() {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String deleteAll = "DELETE FROM files;";
            conn.createStatement().executeUpdate(deleteAll);
            System.out.println("Files table cleared.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean registerUser(String username, String password) throws SQLException {
        // Hash the password using BCrypt
        String hashedPassword = PasswordHashing.hashPassword(password);

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);  // Store the hashed password
            stmt.executeUpdate();
            System.out.println("New user registered: " + username);
            ServerLogs.log("USER_REGISTERED: " + username + " registered successfully.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace(); // Helps debug DB issues
            ServerLogs.log("DB_ERROR: Error registering user '" + username + "'. Error: " + e.getMessage());
            return false;
        }
    }

    // Authenticating user
    public static boolean authenticateUser(String username, String password) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String query = "SELECT password FROM users WHERE username = ?;";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                return PasswordHashing.verifyPassword(password, storedHash);  // Verify with BCrypt
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void saveFile(String username, String filename, long fileSize) {
        try (Connection conn = DriverManager.getConnection(URL)) {
            String query = "INSERT INTO files (username,filename,size) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, filename); // owner
                stmt.setLong(3, fileSize);
                stmt.executeUpdate();
                System.out.println("File record saved in DB for user: " + username);
                ServerLogs.log("FILE_SAVED: User '" + username + "' uploaded file '" + filename + "' (" + fileSize + " bytes).");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            ServerLogs.log("DB_ERROR: Error saving file record for user '" + username + "'. Error: " + e.getMessage());
        }
    }

    // Check if a file already exists for the user
    public static boolean fileExistsForUser(String username, String filename) {
        String query = "SELECT COUNT(*) FROM files WHERE username = ? AND filename = ?";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, filename);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void close() {
        try {
            System.out.println("Database resources released");
        } catch (Exception e) {
            System.err.println("Error closing database resources: " + e.getMessage());
        }
    }
}