package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import org.acme.Entity.Movement;

public record MovementResponseDTO(
    String id,
    String type,
    BigDecimal amount,
    String label,
    Instant date,
    String proofImage
) {
    public static MovementResponseDTO fromEntity(Movement movement) {
        return new MovementResponseDTO(
            movement.id.toHexString(),
            movement.getType(),
            movement.getAmount(),
            movement.getLabel(),
            movement.getDate(),
            movement.getProofImage()
        );
    }
}
