package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.QuesteClosureRequestDTO;
import org.acme.DTO.QuesteRequestDTO;
import org.acme.DTO.QuesteResponseDTO;
import org.acme.Service.Queste.QuesteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QuesteResourceTest {

    @InjectMock
    QuesteService questeService;

    private QuesteResponseDTO sampleQueste;

    @BeforeEach
    void setUp() {
        sampleQueste = new QuesteResponseDTO(
            "64f1a2b3c4d5e6f7a8b9c0d1", 1, "Awa D.", "Produits laitiers", Instant.now(), "en_cours",
            null, new BigDecimal("50.00"), null, null, null, null, List.of()
        );
    }

    private QuesteRequestDTO sampleRequest() {
        return new QuesteRequestDTO("Awa D.", "Produits laitiers", new BigDecimal("50.00"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturnQuestes_whenAuthorized() {
        when(questeService.listAll()).thenReturn(List.of(sampleQueste));

        given()
            .when().get("/questes")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].buyerName", is("Awa D."));
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
            .when().get("/questes")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturnQueste_whenFound() {
        when(questeService.findById("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleQueste);

        given()
            .when().get("/questes/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
            .then()
            .statusCode(200)
            .body("number", is(1));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void create_shouldReturn201_whenPayloadIsValid() {
        when(questeService.create(any(QuesteRequestDTO.class))).thenReturn(sampleQueste);

        given()
            .contentType("application/json")
            .body(sampleRequest())
            .when().post("/questes")
            .then()
            .statusCode(201)
            .body("buyerName", is("Awa D."));
    }

    @Test
    void create_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
            .contentType("application/json")
            .body(sampleRequest())
            .when().post("/questes")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void close_shouldReturnClosedQueste_whenAuthorized() {
        QuesteResponseDTO closed = new QuesteResponseDTO(
            sampleQueste.id(), sampleQueste.number(), sampleQueste.buyerName(), sampleQueste.objectif(),
            sampleQueste.createdAt(), "en_attente_validation", new BigDecimal("500.00"), sampleQueste.primePercent(),
            new BigDecimal("300.00"), new BigDecimal("150.00"), Instant.now(), null, List.of()
        );
        when(questeService.close(org.mockito.ArgumentMatchers.eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(QuesteClosureRequestDTO.class)))
            .thenReturn(closed);

        given()
            .contentType("application/json")
            .body(new QuesteClosureRequestDTO(new BigDecimal("500.00")))
            .when().post("/questes/{id}/cloturer", "64f1a2b3c4d5e6f7a8b9c0d1")
            .then()
            .statusCode(200)
            .body("status", is("en_attente_validation"))
            .body("prime", is(150.0f));
    }

    @Test
    void close_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
            .contentType("application/json")
            .body(new QuesteClosureRequestDTO(BigDecimal.ZERO))
            .when().post("/questes/{id}/cloturer", "64f1a2b3c4d5e6f7a8b9c0d1")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void validate_shouldReturnValidatedQueste_whenAdmin() {
        QuesteResponseDTO validated = new QuesteResponseDTO(
            sampleQueste.id(), sampleQueste.number(), sampleQueste.buyerName(), sampleQueste.objectif(),
            sampleQueste.createdAt(), "cloturee", new BigDecimal("500.00"), sampleQueste.primePercent(),
            new BigDecimal("300.00"), new BigDecimal("150.00"), Instant.now(), Instant.now(), List.of()
        );
        when(questeService.validate("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(validated);

        given()
            .when().post("/questes/{id}/valider", "64f1a2b3c4d5e6f7a8b9c0d1")
            .then()
            .statusCode(200)
            .body("status", is("cloturee"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void validate_shouldReturnForbidden_whenSeller() {
        given()
            .when().post("/questes/{id}/valider", "64f1a2b3c4d5e6f7a8b9c0d1")
            .then()
            .statusCode(403);
    }

    @Test
    void validate_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
            .when().post("/questes/{id}/valider", "64f1a2b3c4d5e6f7a8b9c0d1")
            .then()
            .statusCode(401);
    }
}
