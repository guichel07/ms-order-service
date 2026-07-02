package org.acme.DTO;

import jakarta.validation.constraints.Positive;

/**
 * quantityOrdered
 */
public record QuantityOrdered(
    @Positive(message = "La quantité à ajouter doit être positive") int quantity
) {}
