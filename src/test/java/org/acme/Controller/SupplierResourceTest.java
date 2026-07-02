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
import java.time.Instant;
import java.util.List;
import org.acme.DTO.SupplierArticleDTO;
import org.acme.DTO.SupplierNoteRequestDTO;
import org.acme.DTO.SupplierNoteResponseDTO;
import org.acme.DTO.SupplierRequestDTO;
import org.acme.DTO.SupplierResponseDTO;
import org.acme.Service.Supplier.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SupplierResourceTest {

    @InjectMock
    SupplierService supplierService;

    private SupplierResponseDTO sampleSupplier;

    @BeforeEach
    void setUp() {
        sampleSupplier = new SupplierResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "Fournisseur Marché Total",
                "+242051234567",
                List.of(new SupplierArticleDTO("64f1a2b3c4d5e6f7a8b9c0d2", "Riz parfumé 5kg", "🍚")),
                List.of(new SupplierNoteResponseDTO("Bonne qualité", Instant.now())),
                Instant.now(),
                Instant.now()
        );
    }

    private SupplierRequestDTO sampleRequest() {
        return new SupplierRequestDTO(
                "Fournisseur Marché Total",
                "+242051234567",
                List.of("64f1a2b3c4d5e6f7a8b9c0d2")
        );
    }

    // ---------- GET /suppliers ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturnSuppliers_whenAuthorized() {
        when(supplierService.listAll()).thenReturn(List.of(sampleSupplier));

        given()
                .when().get("/suppliers")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", is("Fournisseur Marché Total"))
                .body("[0].articles[0].name", is("Riz parfumé 5kg"));
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/suppliers")
                .then()
                .statusCode(401);
    }

    // ---------- GET /suppliers/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturnSupplier_whenFound() {
        when(supplierService.findById("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleSupplier);

        given()
                .when().get("/suppliers/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("name", is("Fournisseur Marché Total"));
    }

    // ---------- POST /suppliers ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void create_shouldReturn201_whenPayloadIsValid() {
        when(supplierService.create(any(SupplierRequestDTO.class))).thenReturn(sampleSupplier);

        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().post("/suppliers")
                .then()
                .statusCode(201)
                .body("name", is("Fournisseur Marché Total"));
    }

    @Test
    void create_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().post("/suppliers")
                .then()
                .statusCode(401);
    }

    // ---------- PUT /suppliers/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void update_shouldReturnUpdatedSupplier() {
        when(supplierService.update(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(SupplierRequestDTO.class)))
                .thenReturn(sampleSupplier);

        given()
                .contentType("application/json")
                .body(sampleRequest())
                .when().put("/suppliers/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("name", is("Fournisseur Marché Total"));
    }

    // ---------- POST /suppliers/{id}/notes ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void addNote_shouldReturnUpdatedSupplier_whenAuthorized() {
        when(supplierService.addNote(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(SupplierNoteRequestDTO.class)))
                .thenReturn(sampleSupplier);

        given()
                .contentType("application/json")
                .body(new SupplierNoteRequestDTO("Bonne qualité"))
                .when().post("/suppliers/{id}/notes", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("notes[0].text", is("Bonne qualité"));
    }

    @Test
    void addNote_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(new SupplierNoteRequestDTO("Bonne qualité"))
                .when().post("/suppliers/{id}/notes", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }
}
