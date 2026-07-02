package org.acme.Service.Stats;

import org.acme.DTO.StatsResponseDTO;

public interface StatsService {
    StatsResponseDTO getStats(String period);
}
