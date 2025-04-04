package com.example.group7fileflixserver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// log files for the server
public class ServerLogs {

    // added  in the local ServerLog.txt file
    private static final String LOG_FILE = "ServerLog.txt";

    // File is logged with particular format having user, file, size, activity, status and the time frames
    public static void log(String username, String fileName, long fileSize, String activity, String status) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String logEntry = String.format("[%s] User: %s | File: %s | Size: %d bytes | Activity: %s | Status: %s",
                    timestamp, username, fileName, fileSize, activity, status);
            writer.write(logEntry);
            writer.newLine();
            System.out.println("Log entry added: " + logEntry);
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }
}
