package org.acme.Service.Movement;

import java.util.List;
import org.acme.DTO.MovementRequestDTO;
import org.acme.DTO.MovementResponseDTO;

public interface MovementService {
    List<MovementResponseDTO> listAll();
    MovementResponseDTO create(MovementRequestDTO request);
}
