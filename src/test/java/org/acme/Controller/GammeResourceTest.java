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
import org.acme.DTO.GammeItemRequestDTO;
import org.acme.DTO.GammeItemResponseDTO;
import org.acme.DTO.GammeRequestDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Service.Gamme.GammeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GammeResourceTest {

    @InjectMock
    GammeService gammeService;

    private GammeResponseDTO sampleGamme;

    @BeforeEach
    void setUp() {
        GammeItemResponseDTO pain = new GammeItemResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d2", "Pain", "🍞",
                new BigDecimal("500.00"), 2, new BigDecimal("200.00"), 40,
                new BigDecimal("1000.00"), new BigDecimal("600.00"));
        GammeItemResponseDTO lait = new GammeItemResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d3", "Lait", "🥛",
                new BigDecimal("800.00"), 1, new BigDecimal("300.00"), 25,
                new BigDecimal("800.00"), new BigDecimal("500.00"));
        sampleGamme = new GammeResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "Petit-déjeuner",
                List.of(pain, lait),
                new BigDecimal("1800.00"),
                new BigDecimal("1100.00"),
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private GammeRequestDTO sampleRequest() {
        return new GammeRequestDTO(
                "Petit-déjeuner",
                List.of(
                        new GammeItemRequestDTO("64f1a2b3c4d5e6f7a8b9c0d2", new BigDecimal("500.00"), 2),
                        new GammeItemRequestDTO("64f1a2b3c4d5e6f7a8b9c0d3", new BigDecimal("800.00"), 1)
                )
        );
    }

    // ---------- GET /gammes ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturnGammes_whenAuthorized() {
        when(gammeService.listAll()).thenReturn(List.of(sampleGamme));

        given()
                .when().get("/gammes")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", is("Petit-déjeuner"))
                .body("[0].items[0].name", is("Pain"));
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/gammes")
                .then()
                .statusCode(401);
    }

    // ---------- GET /gammes/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturnGamme_whenFound() {
        when(gammeService.findById("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleGamme);

        given()
                .when().get("/gammes/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("title", is("Petit-déjeuner"))
                .body("totalPrice", is(1800.0f))
                .body("totalMargin", is(1100.0f));
    }

    // ---------- POST /gammes ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void create_shouldReturn201_whenPayloadIsValid() {
        when(gammeService.create(any(GammeRequestDTO.class))).thenReturn(sampleGamme);

        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().post("/gammes")
                .then()
                .statusCode(201)
                .body("title", is("Petit-déjeuner"));
    }

    @Test
    void create_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().post("/gammes")
                .then()
                .statusCode(401);
    }

    // ---------- PUT /gammes/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void update_shouldReturnUpdatedGamme() {
        when(gammeService.update(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(GammeRequestDTO.class)))
                .thenReturn(sampleGamme);

        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().put("/gammes/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("title", is("Petit-déjeuner"));
    }

    // ---------- POST /gammes/{id}/contenu ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void generateContent_shouldReturnGeneratedContent_whenAuthorized() {
        GammeResponseDTO withContent = new GammeResponseDTO(
                sampleGamme.id(), sampleGamme.title(), sampleGamme.items(),
                sampleGamme.totalPrice(), sampleGamme.totalMargin(),
                "Petit-déjeuner : 2x Pain + Lait — 1800.00 F le menu.",
                "Composition reconnue pour \"Petit-déjeuner\" — 2 produits, 61% de marge.",
                Instant.now(), sampleGamme.createdAt(), Instant.now()
        );
        when(gammeService.generateContent("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(withContent);

        given()
                .when().post("/gammes/{id}/contenu", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("marketingText", is("Petit-déjeuner : 2x Pain + Lait — 1800.00 F le menu."))
                .body("patternDescription", is("Composition reconnue pour \"Petit-déjeuner\" — 2 produits, 61% de marge."));
    }

    @Test
    void generateContent_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().post("/gammes/{id}/contenu", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }
}
