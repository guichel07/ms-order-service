package org.acme.DTO;

import java.time.Instant;
import java.util.List;

public record SupplierResponseDTO(
    String id,
    String name,
    String phone,
    List<SupplierArticleDTO> articles,
    /** Note la plus récente en premier — c'est elle qui donne le "dernier contact". */
    List<SupplierNoteResponseDTO> notes,
    Instant createdAt,
    Instant updatedAt
) {}
