package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuesteResponseDTO(
    String id,
    int number,
    String buyerName,
    String objectif,
    Instant createdAt,
    String status,
    BigDecimal transportReel,
    BigDecimal primePercent,
    BigDecimal economieAchats,
    BigDecimal prime,
    Instant closedAt,
    Instant validatedAt,
    List<MissionResponseDTO> missions
) {}
