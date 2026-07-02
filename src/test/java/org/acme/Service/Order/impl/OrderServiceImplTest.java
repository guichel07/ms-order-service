package org.acme.Service.Order.impl;

import jakarta.ws.rs.core.Response;
import org.acme.DTO.ArticleResponseDTO;
import org.acme.DTO.OrderRequestDTO;
import org.acme.DTO.OrderResponseDTO;
import org.acme.Entity.Client;
import org.acme.Entity.Order;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Alert.AlertService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Client buildClient() {
        Client client = new Client();
        client.id = new ObjectId();
        client.setFirstname("Jean");
        client.setLastname("Kabila");
        client.setPhone("0102030405");
        client.setNormalizedPhone("102030405");
        return client;
    }

    private ArticleResponseDTO buildArticleResponseDTO(String name, BigDecimal price) {
        return new ArticleResponseDTO(
                new ObjectId().toHexString(),
                name,
                "icon.png",
                "#FF0000",
                "Boissons",
                price,
                price,
                price,
                price,
                price,
                20,
                price,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                5,
                false,
                false,
                null,
                false,
                null
        );
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

        List<OrderResponseDTO> result = orderService.listAll();

        assertEquals(2, result.size());
    }

    // ---------------------------------------------------------------
    // findById
    // ---------------------------------------------------------------

    @Test
    void findById_shouldReturnOrder_whenItExists() {
        Order order = buildOrder("a@test.com", new Date());
        when(orderRepository.findById(order.id)).thenReturn(order);

        OrderResponseDTO result = orderService.findById(order.id.toHexString());

        assertNotNull(result);
        assertEquals("a@test.com", result.email());
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
        ArticleResponseDTO coffee = buildArticleResponseDTO("Café", BigDecimal.valueOf(2.5));
        ArticleResponseDTO tea = buildArticleResponseDTO("Thé", BigDecimal.valueOf(3.0));

        when(articleService.findById(coffee.id())).thenReturn(coffee);
        when(articleService.findById(tea.id())).thenReturn(tea);
        // Panache assigne l'id au moment du persist() réel ; on simule ce comportement,
        // le mock ne le ferait pas tout seul.
        doAnswer(invocation -> {
            Order persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(orderRepository).persist(any(Order.class));

        Client client = buildClient();
        when(clientRepository.findById(client.id)).thenReturn(client);

        OrderRequestDTO.OrderLineRequestDTO line1 = new OrderRequestDTO.OrderLineRequestDTO(
                coffee.id(), BigDecimal.valueOf(2.5), 2
        );
        OrderRequestDTO.OrderLineRequestDTO line2 = new OrderRequestDTO.OrderLineRequestDTO(
                tea.id(), BigDecimal.valueOf(3.0), 1
        );

        OrderRequestDTO orderDTO = new OrderRequestDTO(
                "John Doe",
                "john@example.com",
                Instant.now(),
                BigDecimal.valueOf(8.0),
                client.id.toHexString(),
                List.of(line1, line2)
        );

        OrderResponseDTO result = orderService.register(orderDTO);

        assertNotNull(result);
        assertEquals("John Doe", result.sellerName());
        assertEquals("john@example.com", result.email());
        assertEquals(2, result.items().size());
        assertTrue(result.receiptNumber().startsWith("CMD-"));

        verify(articleService, times(1)).decrementQuantity(coffee.id(), 2);
        verify(articleService, times(1)).decrementQuantity(tea.id(), 1);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).persist(orderCaptor.capture());
        assertEquals(result.id(), orderCaptor.getValue().id.toHexString());
        assertEquals(result.receiptNumber(), orderCaptor.getValue().getReceiptNumber());
        assertEquals(client.id.toHexString(), result.clientId());
    }

    @Test
    void register_shouldSnapshotUnitCostOnEachLine_fromTheArticlesCostAtSaleTime() {
        ArticleResponseDTO coffee = new ArticleResponseDTO(
                new ObjectId().toHexString(), "Café", "icon.png", "#FF0000", "Boissons",
                BigDecimal.valueOf(2.5), BigDecimal.valueOf(2.5), BigDecimal.valueOf(1.2),
                BigDecimal.valueOf(2.5), BigDecimal.valueOf(2.5), 20,
                BigDecimal.valueOf(1.2), BigDecimal.ZERO, BigDecimal.ZERO, 1, 5, false, false, null, false, null
        );
        when(articleService.findById(coffee.id())).thenReturn(coffee);
        doAnswer(invocation -> {
            Order persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(orderRepository).persist(any(Order.class));

        Client client = buildClient();
        when(clientRepository.findById(client.id)).thenReturn(client);

        OrderRequestDTO.OrderLineRequestDTO line = new OrderRequestDTO.OrderLineRequestDTO(
                coffee.id(), BigDecimal.valueOf(2.5), 3
        );
        OrderRequestDTO orderDTO = new OrderRequestDTO(
                "John Doe", "john@example.com", Instant.now(), BigDecimal.valueOf(7.5),
                client.id.toHexString(), List.of(line)
        );

        orderService.register(orderDTO);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).persist(orderCaptor.capture());
        assertEquals(0, BigDecimal.valueOf(1.2).compareTo(orderCaptor.getValue().getArticles().get(0).getUnitCost()));
    }

    @Test
    void register_shouldThrowNotFound_whenClientIdDoesNotExist() {
        ObjectId missingClientId = new ObjectId();
        when(clientRepository.findById(missingClientId)).thenReturn(null);

        OrderRequestDTO.OrderLineRequestDTO line = new OrderRequestDTO.OrderLineRequestDTO(
                new ObjectId().toHexString(), BigDecimal.valueOf(2.5), 1
        );
        OrderRequestDTO orderDTO = new OrderRequestDTO(
                "John Doe", "john@example.com", Instant.now(), BigDecimal.valueOf(2.5),
                missingClientId.toHexString(), List.of(line)
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.register(orderDTO));

        assertEquals(Response.Status.NOT_FOUND, exception.getErrorCode());
        verify(articleService, never()).findById(anyString());
        verify(orderRepository, never()).persist(any(Order.class));
    }

    @Test
    void register_shouldThrowBadRequest_whenClientIdIsNotAValidObjectId() {
        OrderRequestDTO.OrderLineRequestDTO line = new OrderRequestDTO.OrderLineRequestDTO(
                new ObjectId().toHexString(), BigDecimal.valueOf(2.5), 1
        );
        OrderRequestDTO orderDTO = new OrderRequestDTO(
                "John Doe", "john@example.com", Instant.now(), BigDecimal.valueOf(2.5),
                "not-an-object-id", List.of(line)
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> orderService.register(orderDTO));

        assertEquals(Response.Status.BAD_REQUEST, exception.getErrorCode());
        verify(orderRepository, never()).persist(any(Order.class));
    }

    @Test
    void registerAll_shouldPersistEveryOrder_inOneCall() {
        ArticleResponseDTO coffee = buildArticleResponseDTO("Café", BigDecimal.valueOf(2.5));
        when(articleService.findById(coffee.id())).thenReturn(coffee);
        doAnswer(invocation -> {
            Order persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(orderRepository).persist(any(Order.class));

        Client client = buildClient();
        when(clientRepository.findById(any(ObjectId.class))).thenReturn(client);

        OrderRequestDTO.OrderLineRequestDTO line = new OrderRequestDTO.OrderLineRequestDTO(
                coffee.id(), BigDecimal.valueOf(2.5), 1
        );
        OrderRequestDTO order1 = new OrderRequestDTO(
                "John Doe", "john@example.com", Instant.now(), BigDecimal.valueOf(2.5),
                new ObjectId().toHexString(), List.of(line)
        );
        OrderRequestDTO order2 = new OrderRequestDTO(
                "Jane Doe", "jane@example.com", Instant.now(), BigDecimal.valueOf(2.5),
                new ObjectId().toHexString(), List.of(line)
        );

        List<OrderResponseDTO> result = orderService.registerAll(List.of(order1, order2));

        assertEquals(2, result.size());
        verify(orderRepository, times(2)).persist(any(Order.class));
    }

    @Test
    void register_shouldPropagateNotFound_whenAnArticleDoesNotExist() {
        Client client = buildClient();
        when(clientRepository.findById(client.id)).thenReturn(client);
        when(articleService.findById(anyString()))
                .thenThrow(new BusinessException(Response.Status.NOT_FOUND, "Article not found"));

        OrderRequestDTO.OrderLineRequestDTO line = new OrderRequestDTO.OrderLineRequestDTO(
                new ObjectId().toHexString(), BigDecimal.valueOf(2.5), 1
        );

        OrderRequestDTO orderDTO = new OrderRequestDTO(
                "John Doe",
                "john@example.com",
                Instant.now(),
                BigDecimal.valueOf(2.5),
                client.id.toHexString(),
                List.of(line)
        );

        assertThrows(BusinessException.class, () -> orderService.register(orderDTO));

        verify(orderRepository, org.mockito.Mockito.never()).persist(any(Order.class));
        verify(articleService, org.mockito.Mockito.never()).decrementQuantity(anyString(), anyInt());
    }

    // ---------------------------------------------------------------
    // Alertes (vente à bénéfice négatif — le "Gendarme")
    // ---------------------------------------------------------------

    @Test
    void register_shouldRecordAlert_whenSoldBelowCostPrice() {
        ArticleResponseDTO rice = new ArticleResponseDTO(
                new ObjectId().toHexString(), "Riz 25kg", "icon.png", "#FF0000", "Céréales",
                BigDecimal.valueOf(12000), BigDecimal.valueOf(12000), BigDecimal.valueOf(14000),
                BigDecimal.valueOf(12000), BigDecimal.valueOf(12000), 20,
                BigDecimal.valueOf(14000), BigDecimal.ZERO, BigDecimal.ZERO, 1, 5, false, false, null, false, null
        );
        when(articleService.findById(rice.id())).thenReturn(rice);
        doAnswer(invocation -> {
            Order persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(orderRepository).persist(any(Order.class));

        Client client = buildClient();
        when(clientRepository.findById(client.id)).thenReturn(client);

        OrderRequestDTO.OrderLineRequestDTO line = new OrderRequestDTO.OrderLineRequestDTO(
                rice.id(), BigDecimal.valueOf(12000), 2
        );
        OrderRequestDTO orderDTO = new OrderRequestDTO(
                "Awa Traoré", "awa@example.com", Instant.now(), BigDecimal.valueOf(24000),
                client.id.toHexString(), List.of(line)
        );

        OrderResponseDTO result = orderService.register(orderDTO);

        verify(alertService, times(1)).recordNegativeMarginSale(
                eq(result.id()),
                eq(rice.id()),
                eq("Awa Traoré"),
                eq("Riz 25kg"),
                eq(BigDecimal.valueOf(12000)),
                eq(BigDecimal.valueOf(14000)),
                eq(BigDecimal.valueOf(-2000)),
                eq(2)
        );
    }

    @Test
    void register_shouldNotRecordAlert_whenSoldAtOrAboveCostPrice() {
        ArticleResponseDTO coffee = buildArticleResponseDTO("Café", BigDecimal.valueOf(2.5));
        when(articleService.findById(coffee.id())).thenReturn(coffee);
        doAnswer(invocation -> {
            Order persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(orderRepository).persist(any(Order.class));

        Client client = buildClient();
        when(clientRepository.findById(client.id)).thenReturn(client);

        OrderRequestDTO.OrderLineRequestDTO line = new OrderRequestDTO.OrderLineRequestDTO(
                coffee.id(), BigDecimal.valueOf(2.5), 1
        );
        OrderRequestDTO orderDTO = new OrderRequestDTO(
                "John Doe", "john@example.com", Instant.now(), BigDecimal.valueOf(2.5),
                client.id.toHexString(), List.of(line)
        );

        orderService.register(orderDTO);

        verify(alertService, never()).recordNegativeMarginSale(
                anyString(), anyString(), anyString(), anyString(), any(), any(), any(), anyInt()
        );
    }
}
