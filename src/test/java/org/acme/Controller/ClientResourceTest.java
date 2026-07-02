package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.acme.DTO.ClientDTO;
import org.acme.Exception.BusinessException;
import org.acme.Service.Client.ClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ClientResourceTest {

    @InjectMock
    ClientService clientService;

    private ClientDTO sampleClient;

    @BeforeEach
    void setUp() {
        sampleClient = new ClientDTO("Jean", "Dupont", "0600000001");
    }

    // ---------- GET /clients ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturnClients_whenAuthorized() {
        when(clientService.getAll()).thenReturn(List.of(sampleClient));

        given()
                .when().get("/clients")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].firstname", is("Jean"));

        verify(clientService, times(1)).getAll();
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/clients")
                .then()
                .statusCode(401);
    }

    // ---------- GET /clients/{phone} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getByPhone_shouldReturnClient_whenFound() {
        when(clientService.getByPhone("0600000001")).thenReturn(sampleClient);

        given()
                .when().get("/clients/{phone}", "0600000001")
                .then()
                .statusCode(200)
                .body("phone", is("0600000001"))
                .body("lastname", is("Dupont"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getByPhone_shouldReturn404_whenNotFound() {
        when(clientService.getByPhone("unknown"))
                .thenThrow(new BusinessException(Response.Status.NOT_FOUND, "Client not found unknown"));

        given()
                .when().get("/clients/{phone}", "unknown")
                .then()
                .statusCode(404)
                .body("message", is("Client not found unknown"));
    }

    @Test
    void getByPhone_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/clients/{phone}", "0600000001")
                .then()
                .statusCode(401);
    }

    // ---------- GET /clients/id/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturnClient_whenFound() {
        when(clientService.getById("650f0a1c2b3d4e5f6a7b8c9d")).thenReturn(sampleClient);

        given()
                .when().get("/clients/id/{id}", "650f0a1c2b3d4e5f6a7b8c9d")
                .then()
                .statusCode(200)
                .body("lastname", is("Dupont"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturn404_whenNotFound() {
        when(clientService.getById("unknown"))
                .thenThrow(new BusinessException(Response.Status.NOT_FOUND, "Client not found"));

        given()
                .when().get("/clients/id/{id}", "unknown")
                .then()
                .statusCode(404);
    }

    @Test
    void getById_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/clients/id/{id}", "650f0a1c2b3d4e5f6a7b8c9d")
                .then()
                .statusCode(401);
    }

    // ---------- GET /clients/anonymous/{ageCategory} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getOrCreateAnonymous_shouldReturnAnonymousClient() {
        ClientDTO anonymous = new ClientDTO(
                "anon-id", "Client anonyme", "ENFANT FEMME", null,
                org.acme.Entity.AgeCategory.ENFANT, org.acme.Entity.Gender.FEMME, true
        );
        when(clientService.getOrCreateAnonymous(
                org.acme.Entity.AgeCategory.ENFANT, org.acme.Entity.Gender.FEMME))
                .thenReturn(anonymous);

        given()
                .queryParam("gender", "FEMME")
                .when().get("/clients/anonymous/{ageCategory}", "ENFANT")
                .then()
                .statusCode(200)
                .body("id", is("anon-id"))
                .body("anonymous", is(true));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getOrCreateAnonymous_shouldWorkWithoutGender() {
        ClientDTO anonymous = new ClientDTO(
                "anon-id-2", "Client anonyme", "ADO", null,
                org.acme.Entity.AgeCategory.ADO, null, true
        );
        when(clientService.getOrCreateAnonymous(org.acme.Entity.AgeCategory.ADO, null))
                .thenReturn(anonymous);

        given()
                .when().get("/clients/anonymous/{ageCategory}", "ADO")
                .then()
                .statusCode(200)
                .body("id", is("anon-id-2"));
    }

    @Test
    void getOrCreateAnonymous_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/clients/anonymous/{ageCategory}", "ENFANT")
                .then()
                .statusCode(401);
    }

    // ---------- GET /clients/search ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void searchByName_shouldReturnMatchingClients() {
        when(clientService.searchByName("dup")).thenReturn(List.of(sampleClient));

        given()
                .queryParam("query", "dup")
                .when().get("/clients/search")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].lastname", is("Dupont"));

        verify(clientService).searchByName("dup");
    }

    @Test
    void searchByName_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .queryParam("query", "dup")
                .when().get("/clients/search")
                .then()
                .statusCode(401);
    }

    // ---------- POST /clients ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void saveClient_shouldReturn201_whenPayloadIsValid() {
        when(clientService.saveClient(any(ClientDTO.class))).thenReturn(sampleClient);

        given()
                .contentType("application/json")
                .body(sampleClient)
                .when().post("/clients")
                .then()
                .statusCode(201)
                .body("phone", is("0600000001"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void saveClient_shouldReturn400_whenFirstnameIsBlank() {
        ClientDTO invalid = new ClientDTO("", "Dupont", "0600000001");

        given()
                .contentType("application/json")
                .body(invalid)
                .when().post("/clients")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void saveClient_shouldReturn400_whenPhoneIsBlank() {
        ClientDTO invalid = new ClientDTO("Jean", "Dupont", "");

        given()
                .contentType("application/json")
                .body(invalid)
                .when().post("/clients")
                .then()
                .statusCode(400);
    }

    @Test
    void saveClient_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(sampleClient)
                .when().post("/clients")
                .then()
                .statusCode(401);
    }

    // ---------- POST /clients/{phone}/archive ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void toggleArchiveClient_shouldReturn200_whenFound() {
        when(clientService.toggleArchiveClient("0600000001")).thenReturn(sampleClient);

        given()
                .contentType("application/json")
                .when().post("/clients/{phone}/archive", "0600000001")
                .then()
                .statusCode(200)
                .body("phone", is("0600000001"));

        verify(clientService).toggleArchiveClient("0600000001");
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void toggleArchiveClient_shouldReturn404_whenNotFound() {
        when(clientService.toggleArchiveClient("unknown"))
                .thenThrow(new BusinessException(Response.Status.NOT_FOUND, "Client not found unknown"));

        given()
                .contentType("application/json")
                .when().post("/clients/{phone}/archive", "unknown")
                .then()
                .statusCode(404);
    }

    @Test
    void toggleArchiveClient_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .when().post("/clients/{phone}/archive", "0600000001")
                .then()
                .statusCode(401);
    }

    // ---------- PUT /clients/{phone} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void updateClient_shouldReturnUpdatedClient_whenValid() {
        ClientDTO updated = new ClientDTO("Jean", "Durand", "0600000001");
        when(clientService.updateClient(eq("0600000001"), any(ClientDTO.class))).thenReturn(updated);

        given()
                .contentType("application/json")
                .body(updated)
                .when().put("/clients/{phone}", "0600000001")
                .then()
                .statusCode(200)
                .body("lastname", is("Durand"));

        verify(clientService).updateClient(eq("0600000001"), any(ClientDTO.class));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void updateClient_shouldReturn400_whenPayloadIsInvalid() {
        ClientDTO invalid = new ClientDTO("Jean", "", "0600000001");

        given()
                .contentType("application/json")
                .body(invalid)
                .when().put("/clients/{phone}", "0600000001")
                .then()
                .statusCode(400);
    }

    @Test
    void updateClient_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(sampleClient)
                .when().put("/clients/{phone}", "0600000001")
                .then()
                .statusCode(401);
    }

    // ---------- DELETE /clients/{phone} ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void delete_shouldReturn204_whenFound() {
        given()
                .when().delete("/clients/{phone}", "0600000001")
                .then()
                .statusCode(204);

        verify(clientService, times(1)).deleteClient("0600000001");
    }

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void delete_shouldReturn404_whenNotFound() {
        org.mockito.Mockito.doThrow(new BusinessException(Response.Status.NOT_FOUND, "Client not found unknown"))
                .when(clientService).deleteClient("unknown");

        given()
                .when().delete("/clients/{phone}", "unknown")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void delete_shouldReturn403_whenCallerIsSeller() {
        given()
                .when().delete("/clients/{phone}", "0600000001")
                .then()
                .statusCode(403);
    }

    @Test
    void delete_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().delete("/clients/{phone}", "0600000001")
                .then()
                .statusCode(401);
    }
}