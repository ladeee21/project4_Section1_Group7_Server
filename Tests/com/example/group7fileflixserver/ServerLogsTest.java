package com.example.group7fileflixserver;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

public class ServerLogsTest {

    private static final String LOG_FILE = "ServerLog.txt";

    @Before
    public void setUp() throws IOException {
        // Clear the log file before each test
        Files.write(Paths.get(LOG_FILE), new byte[0]);
    }

    @Test
    public void testLog() throws IOException {
        ServerLogs.log("Alice", "data.txt", 5000, "Upload", "Success");

        List<String> logEntries = Files.readAllLines(Paths.get(LOG_FILE));
        assertEquals(1, logEntries.size());

        String logLine = logEntries.get(0);
        assertTrue(logLine.contains("Alice"));
        assertTrue(logLine.contains("data.txt"));
        assertTrue(logLine.contains("5000"));
        assertTrue(logLine.contains("Upload"));
        assertTrue(logLine.contains("Success"));
    }

    @Test
    public void testMain() throws IOException {
        ServerLogs.main(new String[]{});

        List<String> logEntries = Files.readAllLines(Paths.get(LOG_FILE));
        assertEquals(2, logEntries.size());

        assertTrue(logEntries.get(0).contains("JohnDoe"));
        assertTrue(logEntries.get(1).contains("JaneDoe"));
    }
}
