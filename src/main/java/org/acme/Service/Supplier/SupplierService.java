package org.acme.Service.Supplier;

import java.util.List;
import org.acme.DTO.SupplierNoteRequestDTO;
import org.acme.DTO.SupplierRequestDTO;
import org.acme.DTO.SupplierResponseDTO;

public interface SupplierService {
    SupplierResponseDTO create(SupplierRequestDTO request);

    SupplierResponseDTO update(String id, SupplierRequestDTO request);

    List<SupplierResponseDTO> listAll();

    SupplierResponseDTO findById(String id);

    /** Ajoute une note horodatée à l'historique du fournisseur. */
    SupplierResponseDTO addNote(String id, SupplierNoteRequestDTO request);

    /** Rattache un article au fournisseur s'il ne l'est pas déjà (idempotent) — utilisé à la clôture d'une mission. */
    SupplierResponseDTO linkArticle(String id, String articleId);
}
