package com.example.group7fileflixserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;


public class FileServer {
    private static final int PORT = 55000;
    private static final String UPLOAD_DIR = "server_uploads/";
    private static final String URL = "jdbc:sqlite:C:/Users/navje/project4_Section1_Group7_Server/fileflix.db";

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
                }  else if ("UPLOAD".equals(command)) {
                    handleFileUpload();
                } else if("RETRIEVE".equals(command)){
                    handleClientRequest(socket);
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

        // For handling Register
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

        // Checking if the username is already taken
        private boolean isUsernameTaken(String username) throws SQLException {
            // Check if the username already exists in the database
            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                return rs.getInt(1) > 0; // If count > 0, username exists
            }
        }

        // For registering new user in the database
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

        //Authenticating login
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

        // Handling file uploaf functionality
        private void handleFileUpload() throws IOException {
            String username = input.readUTF();
            String filename = input.readUTF();
            long fileSize = input.readLong();
            File uploadDir = new File("server_uploads");
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File uploadedFile = new File(uploadDir, filename);
            try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }

                // Save file record in the database (filename, size, username)
                Database.saveFile(username, filename, fileSize);

                output.writeUTF("UPLOAD_SUCCESS");
            } catch (IOException e) {
                output.writeUTF("UPLOAD_FAILED");
                e.printStackTrace();
            }
        }

        private static void handleClientRequest(Socket clientSocket) {
            try (InputStream inputStream = clientSocket.getInputStream();
                 OutputStream outputStream = clientSocket.getOutputStream();
                 DataInputStream dis = new DataInputStream(inputStream);
                 DataOutputStream dos = new DataOutputStream(outputStream)) {

                String command = dis.readUTF();

                if ("RETRIEVE".equals(command)) {
                    String filename = dis.readUTF();
                    byte[] fileContent = getFileContentFromDB(filename);

                    if (fileContent != null) {
                        dos.writeLong(fileContent.length);  // Send the file size
                        dos.write(fileContent);  // Send the file data
                    } else {
                        dos.writeLong(0);  // Indicate that the file was not found
                    }
                }
            } catch (IOException e) {
                System.err.println("Error processing client request: " + e.getMessage());
            }
        }

        private static byte[] getFileContentFromDB(String filename) {
            String query = "SELECT file_data FROM files WHERE filename = ?";

            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, filename);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getBytes("file_data"); // Return the file data as a byte array
                }

            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }
            return null;  // File not found
        }
    }
}

