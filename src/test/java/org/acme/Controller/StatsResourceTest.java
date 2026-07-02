package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import jakarta.ws.rs.core.Response.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.acme.DTO.SellerDetailDTO;
import org.acme.DTO.StatsResponseDTO;
import org.acme.Exception.BusinessException;
import org.acme.Service.Stats.SellerDetailService;
import org.acme.Service.Stats.StatsService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StatsResourceTest {

    @InjectMock
    StatsService statsService;

    @InjectMock
    SellerDetailService sellerDetailService;

    private StatsResponseDTO sampleStats() {
        Map<String, StatsResponseDTO.MetricsDTO> bySeller = Map.of(
                "jean.dupont@example.com",
                new StatsResponseDTO.MetricsDTO(new BigDecimal("100.00"), new BigDecimal("30.00"))
        );

        return new StatsResponseDTO(
                bySeller,
                new StatsResponseDTO.PeriodDeltaDTO(new BigDecimal("12.0"), true, "vs 7 jours précédents"),
                List.of(
                        new StatsResponseDTO.TrendBucketDTO(
                                "2026-07-27",
                                "27/07",
                                new BigDecimal("100.00"),
                                new BigDecimal("30.00"),
                                bySeller,
                                "08:32",
                                "19:47",
                                List.of(new StatsResponseDTO.HourlyPointDTO(14, 1, new BigDecimal("100.00")))
                        )
                ),
                "08:32",
                "19:47",
                List.of(new StatsResponseDTO.HourlyPointDTO(14, 1, new BigDecimal("100.00")))
        );
    }

    // ---------- GET /stats/{period} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getStats_shouldReturnStatsData_whenAuthorized() {
        when(statsService.getStats("7j")).thenReturn(sampleStats());

        Response response = given()
                .when().get("/stats/{period}", "7j")
                .then()
                .statusCode(200)
                .body("trend.size()", is(1))
                .extract().response();

        assertEquals(12.0, response.jsonPath().getDouble("delta.pct"), 0.0001);
        assertTrue(response.jsonPath().getBoolean("delta.positive"));

        Map<String, Object> metrics = response.jsonPath().getMap("metrics");
        assertTrue(metrics.containsKey("jean.dupont@example.com"));
    }

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void getStats_shouldBeAccessible_toAdminRole() {
        when(statsService.getStats("7j")).thenReturn(sampleStats());

        given()
                .when().get("/stats/{period}", "7j")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getStats_shouldReturn400_whenPeriodIsUnknown() {
        when(statsService.getStats("unknown"))
                .thenThrow(
                        new BusinessException(
                                Status.BAD_REQUEST,
                                "Période inconnue : unknown. Valeurs autorisées : 7j, 4sem, 6mois, 2ans"
                        )
                );

        given()
                .when().get("/stats/{period}", "unknown")
                .then()
                .statusCode(400)
                .body(
                        "message",
                        is("Période inconnue : unknown. Valeurs autorisées : 7j, 4sem, 6mois, 2ans")
                );
    }

    @Test
    void getStats_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/stats/{period}", "7j")
                .then()
                .statusCode(401);
    }

    // ---------- GET /stats/seller/{email}/detail ----------

    private SellerDetailDTO sampleSellerDetail(String email) {
        return new SellerDetailDTO(
                email,
                new SellerDetailDTO.SellerAlertDTO("warning", "Cadence en baisse de 24% vs la semaine dernière"),
                "08:32",
                "19:47",
                "Aujourd'hui",
                List.of(),
                "7j"
        );
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getSellerDetail_shouldReturnSellerData_whenAuthorized() {
        String email = "jean.dupont@example.com";
        when(sellerDetailService.getDetail(email)).thenReturn(sampleSellerDetail(email));

        given()
                .when().get("/stats/seller/{email}/detail", email)
                .then()
                .statusCode(200)
                .body("email", is(email))
                .body("alert.level", is("warning"))
                .body("firstSaleTime", is("08:32"));
    }

    @Test
    void getSellerDetail_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/stats/seller/{email}/detail", "jean.dupont@example.com")
                .then()
                .statusCode(401);
    }
}
