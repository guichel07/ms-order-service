package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.FlyerRequestDTO;
import org.acme.DTO.FlyerResponseDTO;
import org.acme.DTO.FlyerSectionRequestDTO;
import org.acme.DTO.FlyerSectionResponseDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Service.Flyer.FlyerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FlyerResourceTest {

    @InjectMock
    FlyerService flyerService;

    private FlyerResponseDTO sampleFlyer;

    @BeforeEach
    void setUp() {
        GammeResponseDTO petitDejeuner = new GammeResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d2", "Petit-déjeuner", List.of(),
                new BigDecimal("1800.00"), new BigDecimal("1100.00"),
                null, null, null, Instant.now(), Instant.now());
        sampleFlyer = new FlyerResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "Carte du jour",
                List.of(new FlyerSectionResponseDTO("Petit-déjeuner", petitDejeuner)),
                Instant.now(),
                Instant.now()
        );
    }

    private FlyerRequestDTO sampleRequest() {
        return new FlyerRequestDTO(
                "Carte du jour",
                List.of(new FlyerSectionRequestDTO("Petit-déjeuner", "64f1a2b3c4d5e6f7a8b9c0d2"))
        );
    }

    // ---------- GET /flyers ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturnFlyers_whenAuthorized() {
        when(flyerService.listAll()).thenReturn(List.of(sampleFlyer));

        given()
                .when().get("/flyers")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", is("Carte du jour"))
                .body("[0].sections[0].title", is("Petit-déjeuner"))
                .body("[0].sections[0].gamme.title", is("Petit-déjeuner"));
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/flyers")
                .then()
                .statusCode(401);
    }

    // ---------- GET /flyers/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturnFlyer_whenFound() {
        when(flyerService.findById("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleFlyer);

        given()
                .when().get("/flyers/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("title", is("Carte du jour"));
    }

    // ---------- POST /flyers ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void create_shouldReturn201_whenPayloadIsValid() {
        when(flyerService.create(any(FlyerRequestDTO.class))).thenReturn(sampleFlyer);

        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().post("/flyers")
                .then()
                .statusCode(201)
                .body("title", is("Carte du jour"));
    }

    @Test
    void create_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().post("/flyers")
                .then()
                .statusCode(401);
    }

    // ---------- PUT /flyers/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void update_shouldReturnUpdatedFlyer() {
        when(flyerService.update(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(FlyerRequestDTO.class)))
                .thenReturn(sampleFlyer);

        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().put("/flyers/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("title", is("Carte du jour"));
    }
}
