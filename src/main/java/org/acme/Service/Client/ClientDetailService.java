package org.acme.Service.Client;

import org.acme.DTO.ClientDetailDTO;

public interface ClientDetailService {
    ClientDetailDTO getDetail(String clientId);
}
