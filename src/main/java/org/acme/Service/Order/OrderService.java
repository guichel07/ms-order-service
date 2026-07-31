package org.acme.Service.Order;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.OrderRequestDTO;
import org.acme.DTO.OrderResponseDTO;

public interface OrderService {
    List<OrderResponseDTO> listAll();

    OrderResponseDTO findById(String id);

    void delete(String id);

    OrderResponseDTO register(OrderRequestDTO orderDTO);

    List<OrderResponseDTO> registerAll(List<OrderRequestDTO> orders);

    BigDecimal getTotalSoldTodayByEmail(String email);
}
