package org.acme.DTO;

/** Section enrichie : titre + la gamme complète (déjà enrichie par GammeService) qui la compose. */
public record FlyerSectionResponseDTO(
    String title,
    GammeResponseDTO gamme
) {}
