package org.acme.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Saisie d'un mouvement de trésorerie — la date est toujours fixée côté back, jamais reçue du client. */
public record MovementRequestDTO(
    @Pattern(regexp = "entree|sortie", message = "Type de mouvement invalide")
    String type,

    @DecimalMin(value = "0", inclusive = false, message = "Le montant doit être supérieur à 0")
    BigDecimal amount,

    @NotBlank(message = "Le motif est obligatoire")
    String label,

    String proofImage
) {}
