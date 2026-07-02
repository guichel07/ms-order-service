package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.MovementRequestDTO;
import org.acme.DTO.MovementResponseDTO;
import org.acme.Service.Movement.MovementService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MovementResourceTest {

    @InjectMock
    MovementService movementService;

    private MovementResponseDTO sampleMovement() {
        return new MovementResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "entree",
                BigDecimal.valueOf(50000),
                "Apport initial",
                Instant.parse("2026-07-01T09:00:00Z"),
                null
        );
    }

    private MovementRequestDTO validRequest() {
        return new MovementRequestDTO("entree", BigDecimal.valueOf(50000), "Apport initial", null);
    }

    // ---------- GET /movements ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void listAll_shouldReturn200_whenCallerIsAdmin() {
        when(movementService.listAll()).thenReturn(List.of(sampleMovement()));

        given()
                .when().get("/movements")
                .then()
                .statusCode(200)
                .body("[0].label", is("Apport initial"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturn403_whenCallerIsSeller() {
        given()
                .when().get("/movements")
                .then()
                .statusCode(403);
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/movements")
                .then()
                .statusCode(401);
    }

    // ---------- POST /movements ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void create_shouldReturn201_whenCallerIsAdmin() {
        when(movementService.create(any(MovementRequestDTO.class))).thenReturn(sampleMovement());

        given()
                .contentType("application/json")
                .body(validRequest())
                .when().post("/movements")
                .then()
                .statusCode(201)
                .body("label", is("Apport initial"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void create_shouldReturn403_whenCallerIsSeller() {
        given()
                .contentType("application/json")
                .body(validRequest())
                .when().post("/movements")
                .then()
                .statusCode(403);
    }

    @Test
    void create_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(validRequest())
                .when().post("/movements")
                .then()
                .statusCode(401);
    }
}
