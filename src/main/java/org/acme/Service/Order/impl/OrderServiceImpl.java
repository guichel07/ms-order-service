package org.acme.Service.Order.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.acme.DTO.OrderDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Article.ArticleService;
import org.acme.Service.Order.OrderService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ArticleService articleService;

    public OrderServiceImpl(
        OrderRepository _orderRepository,
        ArticleService _articleService
    ) {
        this.orderRepository = _orderRepository;
        this.articleService = _articleService;
    }

    public List<Order> listAll() {
        return orderRepository.listAll();
    }

    public Order findById(String id) {
        Order orderFound = orderRepository.findById(new ObjectId(id));

        if (orderFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Order not found " + id
            );
        }

        return orderFound;
    }

    @Transactional
    public Order register(OrderDTO orderDTO) {
        ArrayList<OrderItem> items = new ArrayList<>();

        for (OrderDTO.OrderLineDTO line : orderDTO.items()) {
            Article article = articleService.findById(line.articleId());

            items.add(
                new OrderItem(
                    article.id.toHexString(),
                    article.getName(),
                    line.price(),
                    line.quantity()
                )
            );

            articleService.decrementQuantity(line.articleId(), line.quantity());
        }

        Order order = new Order();
        order.setReceiptNumber(generateReceiptNumber());
        order.setReceiptDate(new Date());
        order.setDailySummary(orderDTO.dailySummary());
        order.setSellerName(orderDTO.sellerName());
        order.setSaleDate(orderDTO.saleDate());
        order.setEmail(orderDTO.email());
        order.setArticles(items);

        orderRepository.persist(order);

        return order;
    }

    private String generateReceiptNumber() {
        return (
            "CMD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
    }

    public BigDecimal getTotalSoldTodayByEmail(String email) {
        // Bornes du jour courant : minuit aujourd'hui -> minuit demain
        ZoneId zone = ZoneId.systemDefault();
        Date startOfDay = Date.from(
            LocalDate.now().atStartOfDay(zone).toInstant()
        );
        Date endOfDay = Date.from(
            LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant()
        );

        List<Order> ordersToday = orderRepository.list(
            "email = ?1 and receiptDate >= ?2 and receiptDate < ?3",
            email,
            startOfDay,
            endOfDay
        );

        BigDecimal total = BigDecimal.ZERO;

        for (Order order : ordersToday) {
            for (OrderItem item : order.getArticles()) {
                BigDecimal lineTotal = item
                    .getPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantityOrdered()));
                total = total.add(lineTotal);
            }
        }

        return total;
    }
}
