package org.acme.Service.Mission;

import org.acme.DTO.MissionRequestDTO;
import org.acme.DTO.MissionResponseDTO;
import org.acme.DTO.MissionUpdateDTO;

public interface MissionService {
    /** Crée un achat unitaire rattaché à une quête en_cours. */
    MissionResponseDTO create(String questeId, MissionRequestDTO request);

    /** Corrige qty/prix/statut d'une mission — uniquement tant que sa quête est en_cours. */
    MissionResponseDTO update(String id, MissionUpdateDTO request);
}
