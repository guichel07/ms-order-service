package org.acme.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Thème manuel/occasion défini par l'admin (ex: "Soirée LDC") — l'admin choisit le titre et la
 * fenêtre de dates, le même moteur de lift tourne dessus que pour les tranches horaires.
 */
public record OccasionBundleRequestDTO(
    @NotBlank String title,
    @NotNull Instant start,
    @NotNull Instant end
) {}
