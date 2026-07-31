package org.acme.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Titre + lignes {article, prix, quantité} composées à la main par l'admin — un brouillon de flyer. */
public record GammeRequestDTO(
    @NotBlank String title,
    @Size(min = 2, message = "Une gamme doit contenir au moins 2 lignes") List<@Valid GammeItemRequestDTO> items
) {}
