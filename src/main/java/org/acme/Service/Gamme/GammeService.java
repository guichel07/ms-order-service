package org.acme.Service.Gamme;

import java.util.List;
import org.acme.DTO.GammeRequestDTO;
import org.acme.DTO.GammeResponseDTO;

public interface GammeService {
    GammeResponseDTO create(GammeRequestDTO request);

    GammeResponseDTO update(String id, GammeRequestDTO request);

    List<GammeResponseDTO> listAll();

    GammeResponseDTO findById(String id);

    /** Génère (ou régénère) le texte marketing + le pattern reconnu, et les sauvegarde sur la gamme. */
    GammeResponseDTO generateContent(String id);
}
