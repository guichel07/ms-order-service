package org.acme.DTO;

import java.time.Instant;

public record SupplierNoteResponseDTO(
    String text,
    Instant createdAt
) {}
