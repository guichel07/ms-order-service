package org.acme.DTO;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** Clôture d'une quête : le transport réel de toute la campagne, ventilé ensuite par mission. */
public record QuesteClosureRequestDTO(
    @DecimalMin(value = "0", message = "Le transport réel ne peut pas être négatif") BigDecimal transportReel
) {}
