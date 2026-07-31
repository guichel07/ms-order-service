package org.acme.DTO;

import jakarta.validation.constraints.NotBlank;

public record SupplierNoteRequestDTO(@NotBlank String text) {}
