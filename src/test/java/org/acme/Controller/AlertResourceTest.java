package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.AlertResponseDTO;
import org.acme.Service.Alert.AlertService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AlertResourceTest {

    @InjectMock
    AlertService alertService;

    private AlertResponseDTO sampleAlert(boolean resolved) {
        return new AlertResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "order-1",
                "article-1",
                "Awa Traoré",
                "Sac de riz 25kg",
                BigDecimal.valueOf(12000),
                BigDecimal.valueOf(14000),
                BigDecimal.valueOf(-2000),
                2,
                Instant.parse("2026-07-01T09:00:00Z"),
                resolved
        );
    }

    // ---------- GET /alerts ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturn200_whenCallerIsSeller() {
        when(alertService.listAll()).thenReturn(List.of(sampleAlert(false)));

        given()
                .when().get("/alerts")
                .then()
                .statusCode(200)
                .body("[0].vendor", is("Awa Traoré"));
    }

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void listAll_shouldReturn200_whenCallerIsAdmin() {
        when(alertService.listAll()).thenReturn(List.of(sampleAlert(false)));

        given()
                .when().get("/alerts")
                .then()
                .statusCode(200);
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/alerts")
                .then()
                .statusCode(401);
    }

    // ---------- POST /alerts/{id}/toggle-resolved ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void toggleResolved_shouldReturn200_whenCallerIsAdmin() {
        when(alertService.toggleResolved(anyString())).thenReturn(sampleAlert(true));

        given()
                .when().post("/alerts/{id}/toggle-resolved", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("resolved", is(true));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void toggleResolved_shouldReturn403_whenCallerIsSeller() {
        given()
                .when().post("/alerts/{id}/toggle-resolved", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(403);
    }

    @Test
    void toggleResolved_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().post("/alerts/{id}/toggle-resolved", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }
}
