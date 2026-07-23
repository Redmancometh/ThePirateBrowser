package com.thepiratebrowser.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PutIoTransferTest {
    @Test
    void identifiesActiveAndTerminalTransferStates() {
        assertFalse(new PutIoTransfer(
                1, 0, "Downloading", "DOWNLOADING", 42, "").isDone());
        assertTrue(new PutIoTransfer(
                2, 99, "Ready", "DONE", 100, "").isDone());
        assertTrue(new PutIoTransfer(
                3, 99, "Seeding", "SEEDING", 100, "").isDone());
    }
}
