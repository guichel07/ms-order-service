package org.acme.Service.Flyer;

import java.util.List;
import org.acme.DTO.FlyerRequestDTO;
import org.acme.DTO.FlyerResponseDTO;

public interface FlyerService {
    FlyerResponseDTO create(FlyerRequestDTO request);

    FlyerResponseDTO update(String id, FlyerRequestDTO request);

    List<FlyerResponseDTO> listAll();

    FlyerResponseDTO findById(String id);
}
