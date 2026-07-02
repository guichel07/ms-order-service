package org.acme.DTO;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Titre + contact + articles précis vendus — pas de catégorie, pour un matching fiable plus tard. */
public record SupplierRequestDTO(
    @NotBlank String name,
    @NotBlank String phone,
    List<String> articleIds
) {}
