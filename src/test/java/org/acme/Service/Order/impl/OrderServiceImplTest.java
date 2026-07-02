package org.acme.Service.Order.impl;

import jakarta.ws.rs.core.Response;
import org.acme.DTO.OrderDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Order;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Article.ArticleService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link OrderServiceImpl}.
 * OrderRepository et ArticleService sont mockés : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Article buildArticle(String name, BigDecimal price) {
        Article article = new Article();
        article.id = new ObjectId();
        article.setName(name);
        article.setPrice(price);
        article.setQuantity(20);
        return article;
    }

    private Order buildOrder(String email, Date receiptDate) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setEmail(email);
        order.setReceiptDate(receiptDate);
        order.setArticles(new java.util.ArrayList<>());
        return order;
    }

    // ---------------------------------------------------------------
    // listAll
    // ---------------------------------------------------------------

    @Test
    void listAll_shouldReturnAllOrders() {
        when(orderRepository.listAll()).thenReturn(
                List.of(buildOrder("a@test.com", new Date()), buildOrder("b@test.com", new Date()))
        );

        List<Order> result = orderService.listAll();

        assertEquals(2, result.size());
    }

    // ---------------------------------------------------------------
    // findById
    // ---------------------------------------------------------------

    @Test
    void findById_shouldReturnOrder_whenItExists() {
        Order order = buildOrder("a@test.com", new Date());
        when(orderRepository.findById(order.id)).thenReturn(order);

        Order result = orderService.findById(order.id.toHexString());

        assertNotNull(result);
        assertEquals("a@test.com", result.getEmail());
    }

    @Test
    void findById_shouldThrowNotFound_whenItDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(orderRepository.findById(missingId)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.findById(missingId.toHexString())
        );

        assertEquals(Response.Status.NOT_FOUND, exception.getErrorCode());
    }

    // ---------------------------------------------------------------
    // register
    // ---------------------------------------------------------------

    @Test
    void register_shouldPersistOrder_andDecrementStockForEachLine() {
        Article coffee = buildArticle("Café", BigDecimal.valueOf(2.5));
        Article tea = buildArticle("Thé", BigDecimal.valueOf(3.0));

        when(articleService.findById(coffee.id.toHexString())).thenReturn(coffee);
        when(articleService.findById(tea.id.toHexString())).thenReturn(tea);

        OrderDTO.OrderLineDTO line1 = new OrderDTO.OrderLineDTO(
                coffee.id.toHexString(), BigDecimal.valueOf(2.5), 2
        );
        OrderDTO.OrderLineDTO line2 = new OrderDTO.OrderLineDTO(
                tea.id.toHexString(), BigDecimal.valueOf(3.0), 1
        );

        OrderDTO orderDTO = new OrderDTO(
                "John Doe",
                "john@example.com",
                Instant.now(),
                BigDecimal.valueOf(8.0),
                List.of(line1, line2)
        );

        Order result = orderService.register(orderDTO);

        assertNotNull(result);
        assertEquals("John Doe", result.getSellerName());
        assertEquals("john@example.com", result.getEmail());
        assertEquals(2, result.getArticles().size());
        assertTrue(result.getReceiptNumber().startsWith("CMD-"));

        verify(articleService, times(1)).decrementQuantity(coffee.id.toHexString(), 2);
        verify(articleService, times(1)).decrementQuantity(tea.id.toHexString(), 1);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).persist(orderCaptor.capture());
        assertEquals(result, orderCaptor.getValue());
    }

    @Test
    void register_shouldPropagateNotFound_whenAnArticleDoesNotExist() {
        when(articleService.findById(anyString()))
                .thenThrow(new BusinessException(Response.Status.NOT_FOUND, "Article not found"));

        OrderDTO.OrderLineDTO line = new OrderDTO.OrderLineDTO(
                new ObjectId().toHexString(), BigDecimal.valueOf(2.5), 1
        );

        OrderDTO orderDTO = new OrderDTO(
                "John Doe",
                "john@example.com",
                Instant.now(),
                BigDecimal.valueOf(2.5),
                List.of(line)
        );

        assertThrows(BusinessException.class, () -> orderService.register(orderDTO));

        verify(orderRepository, org.mockito.Mockito.never()).persist(any(Order.class));
        verify(articleService, org.mockito.Mockito.never()).decrementQuantity(anyString(), anyInt());
    }
}