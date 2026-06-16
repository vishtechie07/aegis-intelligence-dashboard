package com.aegis.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RagIndexingServiceTest {

    @Test
    void documentId_isValidUuidAndDeterministic() {
        String id1 = RagIndexingService.documentId(42L, 0);
        String id2 = RagIndexingService.documentId(42L, 0);
        assertThat(id1).isEqualTo(id2);
        assertThat(UUID.fromString(id1)).isNotNull();
    }
}
