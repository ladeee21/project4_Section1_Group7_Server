package com.example.group7fileflixserver;

import static org.junit.Assert.*;

public class FileServerTest {

    @org.junit.Test
    public void main() {
        try {
            Thread serverThread = new Thread(() -> {
                try {
                    FileServer.main(new String[]{});
                } catch (Exception e) {
                    fail("Server failed to start: " + e.getMessage());
                }
            });
            serverThread.start();
            Thread.sleep(2000); // Let the server start up
            serverThread.interrupt(); // Stop thread to avoid blocking the test
        } catch (Exception e) {
            fail("Exception in test: " + e.getMessage());
        }
    }
}