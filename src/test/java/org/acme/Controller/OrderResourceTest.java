package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.OrderDTO;
import org.acme.Entity.Order;
import org.acme.Exception.BusinessException;
import org.acme.Service.Order.OrderService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OrderResourceTest {

    @InjectMock
    OrderService orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order();
        sampleOrder.id = new ObjectId();
        sampleOrder.setReceiptNumber("CMD-ABCD1234");
        sampleOrder.setSellerName("Jean Dupont");
        sampleOrder.setEmail("jean.dupont@example.com");
        sampleOrder.setDailySummary(new BigDecimal("42.50"));
        sampleOrder.setSaleDate(Instant.now());
        sampleOrder.setArticles(new java.util.ArrayList<>());
    }

    private OrderDTO validOrderDTO() {
        return new OrderDTO(
                "Jean Dupont",
                "jean.dupont@example.com",
                Instant.now(),
                new BigDecimal("42.50"),
                List.of(new OrderDTO.OrderLineDTO("64f1a2b3c4d5e6f7a8b9c0d1", new BigDecimal("10.00"), 2))
        );
    }

    // ---------- GET /orders ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturnOrders_whenAuthorized() {
        when(orderService.listAll()).thenReturn(List.of(sampleOrder));

        given()
                .when().get("/orders")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].sellerName", is("Jean Dupont"));

        verify(orderService, times(1)).listAll();
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/orders")
                .then()
                .statusCode(401);
    }

    // ---------- GET /orders/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturnOrder_whenFound() {
        when(orderService.findById("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleOrder);

        given()
                .when().get("/orders/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("email", is("jean.dupont@example.com"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturn404_whenNotFound() {
        when(orderService.findById("unknown-id"))
                .thenThrow(new BusinessException(Response.Status.NOT_FOUND, "Order not found unknown-id"));

        given()
                .when().get("/orders/{id}", "unknown-id")
                .then()
                .statusCode(404)
                .body("message", is("Order not found unknown-id"));
    }

    @Test
    void getById_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/orders/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }

    // ---------- POST /orders ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void register_shouldReturn201_whenPayloadIsValid() {
        when(orderService.register(any(OrderDTO.class))).thenReturn(sampleOrder);

        given()
                .contentType("application/json")
                .body(validOrderDTO())
                .when().post("/orders")
                .then()
                .statusCode(201)
                .body("receiptNumber", is("CMD-ABCD1234"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void register_shouldReturn400_whenSellerNameIsBlank() {
        OrderDTO invalid = new OrderDTO(
                "",
                "jean.dupont@example.com",
                Instant.now(),
                new BigDecimal("42.50"),
                List.of(new OrderDTO.OrderLineDTO("64f1a2b3c4d5e6f7a8b9c0d1", new BigDecimal("10.00"), 2))
        );

        given()
                .contentType("application/json")
                .body(invalid)
                .when().post("/orders")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void register_shouldReturn400_whenItemsListIsEmpty() {
        OrderDTO invalid = new OrderDTO(
                "Jean Dupont",
                "jean.dupont@example.com",
                Instant.now(),
                new BigDecimal("42.50"),
                List.of()
        );

        given()
                .contentType("application/json")
                .body(invalid)
                .when().post("/orders")
                .then()
                .statusCode(400);
    }

    @Test
    void register_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(validOrderDTO())
                .when().post("/orders")
                .then()
                .statusCode(401);
    }

    // ---------- GET /orders/total-today ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getTotalSoldToday_shouldReturnAmount() {
        when(orderService.getTotalSoldTodayByEmail(anyString())).thenReturn(new BigDecimal("123.45"));

        given()
                .queryParam("email", "jean.dupont@example.com")
                .when().get("/orders/total-today")
                .then()
                .statusCode(200)
                .body(is("123.45"));

        verify(orderService).getTotalSoldTodayByEmail("jean.dupont@example.com");
    }

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void getTotalSoldToday_shouldBeAccessible_toAdminRole() {
        when(orderService.getTotalSoldTodayByEmail(anyString())).thenReturn(BigDecimal.ZERO);

        given()
                .queryParam("email", "someone@example.com")
                .when().get("/orders/total-today")
                .then()
                .statusCode(200);
    }
}