package org.acme.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.wildfly.common.annotation.NotNull;

public record OrderRequestDTO(
    @NotBlank(message = "Le nom du vendeur est obligatoire") String sellerName,

    @Email(message = "L'email doit être valide") String email,

    @NotNull Instant saleDate,

    @NotNull BigDecimal dailySummary,

    @NotBlank(message = "L'identifiant du client est obligatoire") String clientId,

    @NotEmpty(message = "La commande doit contenir au moins un article")
    List<OrderLineRequestDTO> items
) {
    public record OrderLineRequestDTO(
        @NotBlank(message = "L'identifiant de l'article est obligatoire")
        String articleId,
        BigDecimal price,
        BigDecimal quantity
    ) {}
}
