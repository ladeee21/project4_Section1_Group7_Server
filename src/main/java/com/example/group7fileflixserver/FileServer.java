package com.example.group7fileflixserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;

public class FileServer {
    private static final int PORT = 55000;
    private static final String UPLOAD_DIR = "server_uploads/";
    private static final String URL = Database.URL;

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

                String command = input.readUTF(); // Expect LOGIN, REGISTER, UPLOAD or RETRIEVE


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
                ServerLogs.log("REGISTER_FAILED: " + username + " attempted to register with an already taken username.");
            } else {
                // Hash the password and register the user
                if (Database.registerUser(username, password)) {
                    output.writeUTF("REGISTER_SUCCESS"); // Send success message
                    ServerLogs.log("REGISTER_SUCCESS: " + username + " registered successfully.");
                } else {
                    output.writeUTF("REGISTER_FAILED"); // Send failure message
                    ServerLogs.log("REGISTER_FAILED: " + username + " registration failed.");
                }
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

        //Authenticating login
        private void handleLogin() throws IOException {
            String username = input.readUTF();
            String password = input.readUTF();

            if (Database.authenticateUser(username, password)) {
                output.writeUTF("AUTH_SUCCESS");
                System.out.println(username + " authenticated successfully.");
                ServerLogs.log("AUTH_SUCCESS: " + username + " logged in successfully.");
            } else {
                output.writeUTF("AUTH_FAILED");
                System.out.println("Authentication failed for user: " + username);
                ServerLogs.log("AUTH_FAILED: " + username + " failed to log in.");
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Handling file upload functionality
        private void handleFileUpload() throws IOException {
            String username = input.readUTF();
            String filename = input.readUTF();
            long fileSize = input.readLong();

            // Check for duplicate
            if (Database.fileExistsForUser(username, filename)) {
                output.writeUTF("DUPLICATE_FILE");
                System.out.println("UPLOAD_REJECTED: Duplicate file '" + filename + "' for user: " + username);

                // Drain incoming data to avoid client-side socket reset
                long remaining = fileSize;
                byte[] buffer = new byte[4096];
                int bytesRead;
                while (remaining > 0 && (bytesRead = input.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    remaining -= bytesRead;
                }

                return;
            }

            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            File uploadedFile = new File(uploadDir, filename);
            try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                long remaining = fileSize;
                byte[] buffer = new byte[4096];
                int bytesRead;
                while (remaining > 0 && (bytesRead = input.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }

                // Save file record in the database (filename, size, username)
                Database.saveFile(username, filename, fileSize);

                output.writeUTF("UPLOAD_SUCCESS");
                System.out.println("UPLOAD_SUCCESS: File '" + filename + "' uploaded by user: " + username);
                ServerLogs.log("UPLOAD_SUCCESS: " + username + " uploaded file '" + filename + "' (" + fileSize + " bytes).");
            } catch (IOException e) {
                output.writeUTF("UPLOAD_FAILED");
                System.err.println("UPLOAD_FAILED: File '" + filename + "' upload failed due to: " + e.getMessage());
                ServerLogs.log("UPLOAD_FAILED: " + username + " failed to upload file '" + filename + "' due to error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private static void handleClientRequest(Socket clientSocket) {
            try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

                // Do not read the command again here — it has already been read
                System.out.println("Handling RETRIEVE request...");

                String username = dis.readUTF();  // Read the logged-in user's username
                String filename = dis.readUTF();  // Read the requested filename

                System.out.println("File retrieval requested: " + filename + " by user: " + username);

                if (!fileBelongsToUser(username, filename)) {
                    dos.writeBoolean(false);
                    dos.writeUTF("ACCESS_DENIED");
                    System.out.println("ACCESS_DENIED: " + username + " tried to access " + filename);
                    ServerLogs.log("ACCESS_DENIED: " + username + " tried to access file '" + filename + "' without permission.");
                    return;
                }

                dos.writeBoolean(true);

                byte[] fileContent = getFileContentFromDisk(filename);
                if (fileContent != null) {
                    dos.writeLong(fileContent.length);
                    dos.write(fileContent);
                    System.out.println("RETRIEVE_SUCCESS: Sent " + filename + " to " + username);
                    ServerLogs.log("RETRIEVE_SUCCESS: Sent file '" + filename + "' to user: " + username);
                } else {
                    dos.writeLong(0);
                    System.out.println("FILE_NOT_FOUND: " + filename + " does not exist on disk.");
                    ServerLogs.log("FILE_NOT_FOUND: File '" + filename + "' not found for user: " + username);
                }

            } catch (IOException e) {
                System.err.println("Error processing client request: " + e.getMessage());
            }
        }

        private static boolean fileBelongsToUser(String username, String filename) {
            String query = "SELECT COUNT(*) FROM files WHERE filename = ? AND username = ?";

            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, filename);
                stmt.setString(2, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                System.err.println("Database error: " + e.getMessage());
            }
            return false;
        }

        private static byte[] getFileContentFromDisk(String filename) {
            File file = new File(UPLOAD_DIR, filename);
            if (!file.exists()) {
                System.out.println("File does not exist: " + filename);
                return null;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] content = new byte[(int) file.length()];
                fis.read(content);
                return content;
            } catch (IOException e) {
                System.err.println("Error reading file: " + e.getMessage());
            }
            return null;
        }
    }
}