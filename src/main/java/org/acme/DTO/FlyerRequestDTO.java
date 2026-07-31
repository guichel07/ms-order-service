package org.acme.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Titre + sections ordonnées composées à la main par l'admin — l'assemblage complet du flyer. */
public record FlyerRequestDTO(
    @NotBlank String title,
    @Size(min = 1, message = "Un flyer doit contenir au moins 1 section") List<@Valid FlyerSectionRequestDTO> sections
) {}
