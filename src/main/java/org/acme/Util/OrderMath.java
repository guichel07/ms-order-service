package org.acme.Util;

import java.math.BigDecimal;
import java.util.List;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;

public final class OrderMath {

    private OrderMath() {}

    public static BigDecimal computeOrderCa(Order order) {
        List<OrderItem> items = order.getArticles();
        if (items == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getPrice().multiply(item.getQuantityOrdered()));
        }
        return total;
    }
}
