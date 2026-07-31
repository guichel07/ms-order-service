package org.acme.DTO;

import java.time.Instant;
import java.util.List;

public record FlyerResponseDTO(
    String id,
    String title,
    List<FlyerSectionResponseDTO> sections,
    Instant createdAt,
    Instant updatedAt
) {}
