package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import java.math.BigDecimal;
import java.time.Instant;
import org.acme.DTO.MissionRequestDTO;
import org.acme.DTO.MissionResponseDTO;
import org.acme.Service.Mission.MissionService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QuesteMissionResourceTest {

    @InjectMock
    MissionService missionService;

    private MissionRequestDTO sampleRequest() {
        return new MissionRequestDTO("a1", 10, new BigDecimal("3000.00"), "terminee", null, null, null, null);
    }

    private MissionResponseDTO sampleResponse() {
        return new MissionResponseDTO(
            "m1", "q1", "a1", "Riz parfumé 5kg", "🍚", null, null, null,
            10, new BigDecimal("3000.00"), new BigDecimal("320.00"), "terminee", Instant.now(), null
        );
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void create_shouldReturn201_whenPayloadIsValid() {
        when(missionService.create(eq("q1"), any(MissionRequestDTO.class))).thenReturn(sampleResponse());

        given()
            .contentType("application/json")
            .body(sampleRequest())
            .when().post("/questes/{questeId}/missions", "q1")
            .then()
            .statusCode(201)
            .body("name", is("Riz parfumé 5kg"));
    }

    @Test
    void create_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
            .contentType("application/json")
            .body(sampleRequest())
            .when().post("/questes/{questeId}/missions", "q1")
            .then()
            .statusCode(401);
    }
}
