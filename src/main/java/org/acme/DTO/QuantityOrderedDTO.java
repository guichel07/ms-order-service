package org.acme.DTO;

import jakarta.validation.constraints.Positive;

/**
 * QuantityOrderedDTO
 */
public record QuantityOrderedDTO(
    @Positive(message = "La quantité à ajouter doit être positive") int quantity
) {}
