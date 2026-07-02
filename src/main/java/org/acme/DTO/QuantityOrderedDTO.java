package org.acme.DTO;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * QuantityOrderedDTO
 */
public record QuantityOrderedDTO(
    @Positive(message = "La quantité à ajouter doit être positive") BigDecimal quantity
) {}
