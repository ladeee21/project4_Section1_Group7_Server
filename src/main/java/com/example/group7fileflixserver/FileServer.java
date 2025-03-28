package com.example.group7fileflixserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;


public class FileServer {
    private static final int PORT = 55000;
    private static final String UPLOAD_DIR = "server_uploads/";
    private static final String URL = "jdbc:sqlite:fileflix.db";

    public static void main(String[] args) {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // Initialize database
        Database.initialize();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                String command = input.readUTF(); // Expect LOGIN or REGISTER


                if ("REGISTER".equals(command)) {
                    handleRegister();
                } else if ("LOGIN".equals(command)) {
                    handleLogin();
                }
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleRegister() throws IOException, SQLException {
            String username = input.readUTF();
            String password = input.readUTF();

            if (isUsernameTaken(username)) {
                output.writeUTF("REGISTER_FAILED"); // Send failure message
            } else {
                registerUser(username, password);
                output.writeUTF("REGISTER_SUCCESS"); // Send success message
            }
        }

        private boolean isUsernameTaken(String username) throws SQLException {
            // Check if the username already exists in the database
            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                return rs.getInt(1) > 0; // If count > 0, username exists
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



        private void handleLogin() throws IOException {
            String username = input.readUTF();
            String password = input.readUTF();

            if (Database.authenticateUser(username, password)) {
                output.writeUTF("AUTH_SUCCESS");
                System.out.println(username + " authenticated successfully.");
            } else {
                output.writeUTF("AUTH_FAILED");
                socket.close();
            }
        }



        private void handleFileUpload() throws IOException {
            String username = input.readUTF();
            String fileName = input.readUTF();
            long fileSize = input.readLong();

            File file = new File(UPLOAD_DIR + fileName);
            FileOutputStream fileOutput = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;

            while (totalBytesRead < fileSize && (bytesRead = input.read(buffer)) != -1) {
                fileOutput.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            fileOutput.close();

            // Store file in database
            Database.saveFileRecord(username, fileName, file.getAbsolutePath());

            System.out.println("File " + fileName + " uploaded by " + username);
            output.writeUTF("UPLOAD_SUCCESS");
        }
    }
}

