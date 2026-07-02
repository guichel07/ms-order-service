package org.acme.DTO;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record QuantityAdjustmentDTO(
    @Positive(message = "La quantité à ajouter doit être positive") BigDecimal quantity
) {}
