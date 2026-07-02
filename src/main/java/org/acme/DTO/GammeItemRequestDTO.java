package org.acme.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** Une ligne du flyer : article + prix et quantité choisis par l'admin (pas forcément le prix catalogue). */
public record GammeItemRequestDTO(
    @NotBlank String articleId,
    @DecimalMin(value = "0.01", message = "Le prix doit être positif") BigDecimal price,
    @Min(value = 1, message = "La quantité doit être d'au moins 1") int quantity
) {}
