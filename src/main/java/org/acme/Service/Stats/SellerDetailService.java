package org.acme.Service.Stats;

import org.acme.DTO.SellerDetailDTO;

public interface SellerDetailService {
    SellerDetailDTO getDetail(String email);
}
