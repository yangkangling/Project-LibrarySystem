package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageLocationServiceTest {
    private final StorageLocationService service = new StorageLocationService(null, null, null, null);

    @Test
    void normalizesShelfLocation() {
        assertEquals("D-05-14", service.normalizeShelfLocation(" d-5-14 "));
        assertEquals("A-50-50", service.normalizeShelfLocation("A-50-50"));
    }

    @Test
    void rejectsShelfNumbersOutsideLimit() {
        assertThrows(RuntimeException.class, () -> service.normalizeShelfLocation("D-51-14"));
        assertThrows(RuntimeException.class, () -> service.normalizeShelfLocation("D-50-51"));
        assertThrows(RuntimeException.class, () -> service.normalizeShelfLocation("D-00-14"));
    }

    @Test
    void rejectsInvalidShelfFormat() {
        assertThrows(RuntimeException.class, () -> service.normalizeShelfLocation("D-1"));
        assertThrows(RuntimeException.class, () -> service.normalizeShelfLocation("D-001-14"));
        assertThrows(RuntimeException.class, () -> service.normalizeShelfLocation(""));
    }
}
