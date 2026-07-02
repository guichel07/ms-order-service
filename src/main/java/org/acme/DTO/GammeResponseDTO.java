package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GammeResponseDTO(
    String id,
    String title,
    List<GammeItemResponseDTO> items,
    BigDecimal totalPrice,
    BigDecimal totalMargin,
    /** Null tant que le texte n'a jamais été généré. */
    String marketingText,
    /** Pattern reconnu par l'IA (ex: "petit-déjeuner congolais") — null tant que jamais généré. */
    String patternDescription,
    Instant contentGeneratedAt,
    Instant createdAt,
    Instant updatedAt
) {}
