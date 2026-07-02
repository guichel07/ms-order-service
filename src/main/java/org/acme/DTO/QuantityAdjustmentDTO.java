package org.acme.DTO;

import jakarta.validation.constraints.Positive;

public record QuantityAdjustmentDTO(
    @Positive(message = "La quantité à ajouter doit être positive") int quantity
) {}
