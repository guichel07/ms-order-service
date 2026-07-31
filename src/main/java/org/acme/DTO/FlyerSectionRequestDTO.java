package org.acme.DTO;

import jakarta.validation.constraints.NotBlank;

/** Une section du flyer : titre affiché + id de la gamme qui la compose. */
public record FlyerSectionRequestDTO(
    @NotBlank String title,
    @NotBlank String gammeId
) {}
