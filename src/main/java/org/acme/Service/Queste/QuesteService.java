package org.acme.Service.Queste;

import java.util.List;
import org.acme.DTO.QuesteClosureRequestDTO;
import org.acme.DTO.QuesteRequestDTO;
import org.acme.DTO.QuesteResponseDTO;

public interface QuesteService {
    QuesteResponseDTO create(QuesteRequestDTO request);

    List<QuesteResponseDTO> listAll();

    QuesteResponseDTO findById(String id);

    /**
     * Clôture la quête : calcule l'économie/prime depuis les missions terminées, ventile le
     * transport réel, et reporte les missions en_attente/ecartee vers la prochaine quête active
     * du même acheteur (créée si besoin) pour ne rien perdre.
     */
    QuesteResponseDTO close(String id, QuesteClosureRequestDTO request);

    /**
     * Validation admin : injecte chaque mission terminée dans le stock et le coût de revient de
     * son article (transport ventilé au prorata de la valeur d'achat), puis clôture définitivement.
     */
    QuesteResponseDTO validate(String id);
}
