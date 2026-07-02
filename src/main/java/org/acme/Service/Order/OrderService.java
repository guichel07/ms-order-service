package org.acme.Service.Order;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.OrderDTO;
import org.acme.Entity.Order;

public interface OrderService {
    List<Order> listAll();

    Order findById(String id);

    Order register(OrderDTO orderDTO);

    BigDecimal getTotalSoldTodayByEmail(String email);
}
