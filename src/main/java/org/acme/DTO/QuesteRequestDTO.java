package org.acme.DTO;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** Ouverture d'une quête : acheteur, % de prime, et un objectif libre optionnel. */
public record QuesteRequestDTO(
    @NotBlank String buyerName,
    String objectif,
    @DecimalMin(value = "0", message = "Le pourcentage de prime doit être entre 0 et 100")
    @DecimalMax(value = "100", message = "Le pourcentage de prime doit être entre 0 et 100")
    BigDecimal primePercent
) {}
