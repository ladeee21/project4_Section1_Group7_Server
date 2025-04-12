package com.example.group7fileflixserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class FileServer {
    private static final int PORT = 55000;
    private static final String UPLOAD_DIR = "server_uploads/";
    private static final String URL = Database.URL;
    private static final long CLIENT_TIMEOUT = 120000;
    private static final long SERVER_SHUTDOWN_TIMEOUT = 120000;
    private static final Set<Socket> activeConnections = Collections.synchronizedSet(new HashSet<>());
    private static final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());

    public static void main(String[] args) {
        initializeServer();
    }

    private static void initializeServer() {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            System.err.println("Failed to create upload directory");
            return;
        }

        Database.initialize();
        setupShutdownHook();
        startShutdownMonitor();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            System.out.println("Waiting for connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientIP = clientSocket.getInetAddress().getHostAddress();

                activeConnections.add(clientSocket);
                lastActivityTime.set(System.currentTimeMillis());

                System.out.printf("[%s] New connection established\n", clientIP);
                System.out.println("Active connections: " + activeConnections.size());

                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nInitiating graceful shutdown...");
            ServerLogs.log("Initiating graceful shutdown...");
            synchronized (activeConnections) {
                System.out.println("Closing " + activeConnections.size() + " active connections");
                ServerLogs.log("Closing " + activeConnections.size() + " active connections");
                activeConnections.forEach(socket -> {
                    try {
                        if (!socket.isClosed()) {
                            socket.close();
                            System.out.println("Closed connection: " + socket.getInetAddress());
                        }
                    } catch (IOException e) {
                        System.err.println("Error closing socket: " + e.getMessage());
                    }
                });
                activeConnections.clear();
            }
            Database.close();
            System.out.println("Server shutdown complete");
        }));
    }

    private static void startShutdownMonitor() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // Check every minute
                    long inactiveDuration = System.currentTimeMillis() - lastActivityTime.get();
                    if (inactiveDuration > SERVER_SHUTDOWN_TIMEOUT && activeConnections.isEmpty()) {
                        System.out.println("Server inactive - initiating shutdown");
                        ServerLogs.log("Server inactive - initiating shutdown");
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Shutdown monitor interrupted");
                }
            }
        }).start();
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private final String clientIP;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientIP = socket.getInetAddress().getHostAddress();
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout((int) CLIENT_TIMEOUT);
                input = new DataInputStream(socket.getInputStream());
                output = new DataOutputStream(socket.getOutputStream());

                System.out.printf("[%s] Client handler started\n", clientIP);
                ServerLogs.log("Client connected from: " + clientIP);

                while (!socket.isClosed()) {
                    String command = input.readUTF();
                    lastActivityTime.set(System.currentTimeMillis());

                    System.out.printf("[%s] Received command: %s\n", clientIP, command);
                    ServerLogs.log(String.format("[%s] Command: %s", clientIP, command));

                    switch (command) {
                        case "REGISTER":
                            handleRegister();
                            break;
                        case "LOGIN":
                            handleLogin();
                            break;
                        case "UPLOAD":
                            handleFileUpload();
                            break;
                        case "RETRIEVE":
                            handleClientRequest();
                            break;
                        case "LOGOUT":
                            handleLogout();
                            return; // Exit the loop after logout
                        case "HEARTBEAT":
                            handleHeartbeat();
                            break;
                        default:
                            System.out.printf("[%s] Unknown command: %s\n", clientIP, command);
                            output.writeUTF("UNKNOWN_COMMAND");
                            output.flush();
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.printf("[%s] Client idle timeout\n", clientIP);
                ServerLogs.log("Client timeout: " + clientIP);
            } catch (IOException e) {
                System.err.printf("[%s] Connection error: %s\n", clientIP, e.getMessage());
                ServerLogs.log("Connection error from " + clientIP + ": " + e.getClass().getSimpleName());
            } catch (SQLException e) {
                System.err.printf("[%s] Database error: %s\n", clientIP, e.getMessage());
                ServerLogs.log("Database error for client " + clientIP);
            } finally {
                closeConnection();
            }
        }

        private void handleLogout() throws IOException {
            String username = input.readUTF();
            output.writeUTF("LOGOUT_SUCCESS");
            output.flush();

            System.out.printf("[%s] User logged out: %s\n", clientIP, username);
            ServerLogs.log(String.format("User %s logged out from %s", username, clientIP));
        }

        private void handleHeartbeat() throws IOException {
            output.writeUTF("HEARTBEAT_ACK");
            output.flush();
            System.out.printf("[%s] Heartbeat acknowledged\n", clientIP);
        }

        private void closeConnection() {
            try {
                if (socket != null && !socket.isClosed()) {
                    System.out.printf("[%s] Closing connection\n", clientIP);
                    ServerLogs.log("[Closing connection\n"+ clientIP);
                    socket.close();
                    activeConnections.remove(socket);
                    System.out.printf("[%s] Connection closed. Active connections: %d\n",
                            clientIP, activeConnections.size());
                    ServerLogs.log("Client disconnected: " + clientIP);
                }
            } catch (IOException e) {
                System.err.printf("[%s] Error closing connection: %s\n", clientIP, e.getMessage());
            }
        }

        private void handleRegister() throws IOException, SQLException {
            String username = input.readUTF();
            String password = input.readUTF();

            // Validate inputs first
            if (username == null || username.trim().isEmpty() ||
                    password == null || password.isEmpty()) {
                output.writeUTF("REGISTER_FAILED");
                System.out.printf("[%s] Registration failed - empty fields\n", clientIP);
                return;
            }

            if (username.length() < 4 || password.length() < 6) {
                output.writeUTF("REGISTER_FAILED");
                System.out.printf("[%s] Registration failed - invalid credentials format\n", clientIP);
                return;
            }

            try {
                if (isUsernameTaken(username)) {
                    output.writeUTF("USERNAME_TAKEN");
                    System.out.printf("[%s] Registration failed - username taken: %s\n",
                            clientIP, username);
                    ServerLogs.log(String.format("[%s] Username taken: %s", clientIP, username));
                } else if (Database.registerUser(username, password)) {
                    output.writeUTF("REGISTER_SUCCESS");
                    System.out.printf("[%s] Registration successful: %s\n",
                            clientIP, username);
                    ServerLogs.log(String.format("[%s] New user registered: %s",
                            clientIP, username));
                } else {
                    output.writeUTF("REGISTER_FAILED");
                    System.out.printf("[%s] Registration failed (database error): %s\n",
                            clientIP, username);
                    ServerLogs.log(String.format("[%s] Registration failed for: %s",
                            clientIP, username));
                }
            } catch (SQLException e) {
                output.writeUTF("REGISTER_FAILED");
                System.err.printf("[%s] Database error during registration: %s\n",
                        clientIP, e.getMessage());
                ServerLogs.log(String.format("[%s] Database error: %s",
                        clientIP, e.getMessage()));
            }
        }

        private boolean isUsernameTaken(String username) throws SQLException {
            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM users WHERE username = ?")) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            }
        }

        private void handleLogin() throws IOException, SQLException {
            String username = input.readUTF();
            String password = input.readUTF();

            if (Database.authenticateUser(username, password)) {
                output.writeUTF("AUTH_SUCCESS");
                System.out.printf("[%s] Login successful: %s\n", clientIP, username);
                ServerLogs.log(String.format("[%s] User logged in: %s", clientIP, username));
            } else {
                output.writeUTF("AUTH_FAILED");
                System.out.printf("[%s] Login failed: %s\n", clientIP, username);
                ServerLogs.log(String.format("[%s] Failed login attempt: %s", clientIP, username));
                closeConnection();
            }
        }

        private void handleFileUpload() throws IOException {
            String username = input.readUTF();
            String filename = input.readUTF();
            long fileSize = input.readLong();

            if (Database.fileExistsForUser(username, filename)) {
                output.writeUTF("DUPLICATE_FILE");
                System.out.printf("[%s] Duplicate file rejected: %s by %s\n", clientIP, filename, username);
                ServerLogs.log(String.format("[%s] Duplicate file: %s by %s", clientIP, filename, username));
                drainInput(fileSize);
                return;
            }

            File uploadedFile = new File(UPLOAD_DIR, filename);
            try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                long remaining = fileSize;
                byte[] buffer = new byte[4096];
                while (remaining > 0) {
                    int bytesRead = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (bytesRead == -1) break;
                    fos.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }

                Database.saveFile(username, filename, fileSize);
                output.writeUTF("UPLOAD_SUCCESS");
                System.out.printf("[%s] File uploaded: %s by %s (%d bytes)\n",
                        clientIP, filename, username, fileSize);
                ServerLogs.log(String.format("[%s] File uploaded: %s by %s", clientIP, filename, username));
            } catch (IOException e) {
                output.writeUTF("UPLOAD_FAILED");
                System.err.printf("[%s] Upload failed: %s\n", clientIP, e.getMessage());
                ServerLogs.log(String.format("[%s] Upload failed: %s", clientIP, e.getMessage()));
            }
        }

        private void drainInput(long bytesToDrain) throws IOException {
            long remaining = bytesToDrain;
            byte[] buffer = new byte[4096];
            while (remaining > 0) {
                int bytesRead = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (bytesRead == -1) break;
                remaining -= bytesRead;
            }
        }

        private void handleClientRequest() throws IOException {
            String username = input.readUTF();
            String filename = input.readUTF();

            System.out.printf("[%s] File request: %s by %s\n", clientIP, filename, username);
            ServerLogs.log(String.format("[%s] File request: %s by %s", clientIP, filename, username));

            if (!fileBelongsToUser(username, filename)) {
                output.writeBoolean(false);
                output.writeUTF("ACCESS_DENIED");
                System.out.printf("[%s] Access denied: %s for %s\n", clientIP, filename, username);
                ServerLogs.log(String.format("[%s] Access denied: %s", clientIP, filename));
                return;
            }

            output.writeBoolean(true);
            byte[] fileContent = getFileContentFromDisk(filename);
            if (fileContent != null) {
                output.writeLong(fileContent.length);
                output.write(fileContent);
                System.out.printf("[%s] File sent: %s to %s (%d bytes)\n",
                        clientIP, filename, username, fileContent.length);
                ServerLogs.log(String.format("[%s] File sent: %s", clientIP, filename));
            } else {
                output.writeLong(0);
                System.out.printf("[%s] File not found: %s\n", clientIP, filename);
                ServerLogs.log(String.format("[%s] File not found: %s", clientIP, filename));
            }
        }

        private static boolean fileBelongsToUser(String username, String filename) {
            try (Connection conn = DriverManager.getConnection(URL);
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT COUNT(*) FROM files WHERE filename = ? AND username = ?")) {
                stmt.setString(1, filename);
                stmt.setString(2, username);
                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            } catch (SQLException e) {
                System.err.println("Database error checking file ownership: " + e.getMessage());
                return false;
            }
        }

        private static byte[] getFileContentFromDisk(String filename) {
            File file = new File(UPLOAD_DIR, filename);
            if (!file.exists()) {
                return null;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] content = new byte[(int) file.length()];
                fis.read(content);
                return content;
            } catch (IOException e) {
                System.err.println("Error reading file: " + e.getMessage());
                return null;
            }
        }
    }
}