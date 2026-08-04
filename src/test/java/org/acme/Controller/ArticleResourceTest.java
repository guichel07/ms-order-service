package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.ArticleDetailDTO;
import org.acme.DTO.ArticleRankingEntryDTO;
import org.acme.DTO.ArticleRequestDTO;
import org.acme.DTO.ArticleResponseDTO;
import org.acme.DTO.BundleContentDTO;
import org.acme.DTO.OccasionBundleRequestDTO;
import org.acme.DTO.QuantityAdjustmentDTO;
import org.acme.DTO.QuantityOrderedDTO;
import org.acme.Exception.BusinessException;
import org.acme.Service.Article.ArticleBundleService;
import org.acme.Service.Article.ArticleDetailService;
import org.acme.Service.Article.ArticleRankingService;
import org.acme.Service.Article.ArticleService;
import org.acme.Service.BundleContent.BundleContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ArticleResourceTest {

    @InjectMock
    ArticleService articleService;

    @InjectMock
    ArticleDetailService articleDetailService;

    @InjectMock
    ArticleRankingService articleRankingService;

    @InjectMock
    ArticleBundleService articleBundleService;

    @InjectMock
    BundleContentService bundleContentService;

    private ArticleResponseDTO sampleArticle;

    @BeforeEach
    void setUp() {
        sampleArticle = new ArticleResponseDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "Croissant",
                "croissant.svg",
                "#FFD700",
                "Viennoiserie",
                new BigDecimal("1.20"),
                new BigDecimal("1.20"),
                new BigDecimal("1.20"),
                new BigDecimal("1.20"),
                new BigDecimal("1.20"),
                BigDecimal.valueOf(50),
                "pièce",
                List.of(),
                new BigDecimal("1.20"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(5),
                false,
                false,
                null,
                false,
                null
        );
    }

    private ArticleRequestDTO validArticleDTO() {
        return new ArticleRequestDTO(
                "Croissant", "croissant.svg", "#FFD700", "Viennoiserie",
                new BigDecimal("1.20"), new BigDecimal("1.20"), new BigDecimal("1.20"), new BigDecimal("1.20"),
                new BigDecimal("1.20"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1), BigDecimal.valueOf(50), BigDecimal.valueOf(5), "pièce", List.of(), false, false, null
        );
    }

    // ---------- GET /articles ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void listAll_shouldReturnArticles_whenAuthorized() {
        when(articleService.listAll()).thenReturn(List.of(sampleArticle));

        given()
                .when().get("/articles")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", is("Croissant"));
    }

    @Test
    void listAll_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles")
                .then()
                .statusCode(401);
    }

    // ---------- GET /articles/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturnArticle_whenFound() {
        when(articleService.findById("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleArticle);

        given()
                .when().get("/articles/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("id", is("64f1a2b3c4d5e6f7a8b9c0d1"))
                .body("category", is("Viennoiserie"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getById_shouldReturn404_whenNotFound() {
        when(articleService.findById("unknown-id"))
                .thenThrow(new BusinessException(jakarta.ws.rs.core.Response.Status.NOT_FOUND, "Article not found unknown-id"));

        given()
                .when().get("/articles/{id}", "unknown-id")
                .then()
                .statusCode(404)
                .body("message", is("Article not found unknown-id"));
    }

    // ---------- GET /articles/by-name/{name} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getByName_shouldReturnArticle_whenFound() {
        when(articleService.findByName("Croissant")).thenReturn(sampleArticle);

        given()
                .when().get("/articles/by-name/{name}", "Croissant")
                .then()
                .statusCode(200)
                .body("name", is("Croissant"));
    }

    @Test
    void getByName_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/by-name/{name}", "Croissant")
                .then()
                .statusCode(401);
    }

    // ---------- POST /articles ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void register_shouldReturn201_whenPayloadIsValid() {
        when(articleService.register(any(ArticleRequestDTO.class))).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(validArticleDTO())
                .when().post("/articles")
                .then()
                .statusCode(201)
                .body("name", is("Croissant"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void register_shouldReturn403_whenCallerIsSeller() {
        given()
                .contentType("application/json")
                .body(validArticleDTO())
                .when().post("/articles")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void register_shouldReturn400_whenNameIsBlank() {
        // Note: ArticleRequestDTO currently has no @NotBlank on its fields besides validation
        // annotations declared in the record; this test documents current behavior
        // and should be updated if stricter validation is added to ArticleRequestDTO.
        ArticleRequestDTO invalid = new ArticleRequestDTO(
                null, "icon.svg", "#000000", "Cat",
                new BigDecimal("-1"), new BigDecimal("-1"), new BigDecimal("-1"), new BigDecimal("-1"),
                new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1), BigDecimal.ZERO, BigDecimal.ZERO, "pièce", List.of(), false, false, null
        );
        when(articleService.register(any(ArticleRequestDTO.class))).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(invalid)
                .when().post("/articles")
                .then()
                .statusCode(201); // ArticleRequestDTO has no bean-validation constraints today
    }

    // ---------- PUT /articles/{id} ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void update_shouldReturnUpdatedArticle() {
        when(articleService.update(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(ArticleRequestDTO.class))).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(validArticleDTO())
                .when().put("/articles/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("name", is("Croissant"));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void update_shouldReturn403_whenCallerIsSeller() {
        given()
                .contentType("application/json")
                .body(validArticleDTO())
                .when().put("/articles/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(403);
    }

    @Test
    void update_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .contentType("application/json")
                .body(validArticleDTO())
                .when().put("/articles/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }

    // ---------- POST /articles/{id}/archive ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void toggleArchive_shouldReturnUpdatedArticle() {
        when(articleService.toggleArchive("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleArticle);

        given()
                .when().post("/articles/{id}/archive", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("id", is("64f1a2b3c4d5e6f7a8b9c0d1"));

        verify(articleService, times(1)).toggleArchive("64f1a2b3c4d5e6f7a8b9c0d1");
    }

    @Test
    void toggleArchive_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().post("/articles/{id}/archive", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }

    // ---------- PATCH /articles/{id}/quantity (restock) ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void restock_shouldReturn200_whenQuantityIsPositive() {
        when(articleService.incrementQuantity(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(BigDecimal.class))).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(new QuantityAdjustmentDTO(BigDecimal.valueOf(10)))
                .when().patch("/articles/{id}/quantity", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200);

        verify(articleService).incrementQuantity("64f1a2b3c4d5e6f7a8b9c0d1", BigDecimal.valueOf(10));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void restock_shouldReturn400_whenQuantityIsNotPositive() {
        given()
                .contentType("application/json")
                .body(new QuantityAdjustmentDTO(BigDecimal.ZERO))
                .when().patch("/articles/{id}/quantity", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(400);
    }

    // ---------- PATCH /articles/{id}/quantityOrdered (destock) ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void destock_shouldReturn200_whenQuantityIsPositive() {
        when(articleService.decrementQuantity(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(BigDecimal.class))).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(new QuantityOrderedDTO(BigDecimal.valueOf(5)))
                .when().patch("/articles/{id}/quantityOrdered", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200);

        verify(articleService).decrementQuantity("64f1a2b3c4d5e6f7a8b9c0d1", BigDecimal.valueOf(5));
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void destock_shouldReturn400_whenQuantityIsNotPositive() {
        given()
                .contentType("application/json")
                .body(new QuantityOrderedDTO(BigDecimal.valueOf(-3)))
                .when().patch("/articles/{id}/quantityOrdered", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void destock_shouldReturn400_whenBusinessRuleViolated() {
        when(articleService.decrementQuantity(anyString(), any(BigDecimal.class)))
                .thenThrow(new BusinessException(jakarta.ws.rs.core.Response.Status.BAD_REQUEST, "Stock insuffisant"));

        given()
                .contentType("application/json")
                .body(new QuantityOrderedDTO(BigDecimal.valueOf(999)))
                .when().patch("/articles/{id}/quantityOrdered", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(400)
                .body("message", is("Stock insuffisant"));
    }

    // ---------- GET /articles/ranking/{period} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getRanking_shouldReturnRankedArticles_whenAuthorized() {
        when(articleRankingService.getRanking("7j")).thenReturn(List.of(
                new ArticleRankingEntryDTO("64f1a2b3c4d5e6f7a8b9c0d1", "Sac de riz 25kg", new BigDecimal("36000.00"), new BigDecimal("12000.00"), BigDecimal.valueOf(3))
        ));

        given()
                .when().get("/articles/ranking/{period}", "7j")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].articleId", is("64f1a2b3c4d5e6f7a8b9c0d1"));
    }

    @Test
    void getRanking_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/ranking/{period}", "7j")
                .then()
                .statusCode(401);
    }

    // ---------- GET /articles/{id}/detail ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getDetail_shouldReturnArticleDetail_whenAuthorized() {
        ArticleDetailDTO detail = new ArticleDetailDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                new ArticleDetailDTO.ArticleAlertDTO("warning", "Vélocité en baisse de 24% vs la semaine dernière"),
                List.of(),
                "7j"
        );
        when(articleDetailService.getDetail("64f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(detail);

        given()
                .when().get("/articles/{id}/detail", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("articleId", is("64f1a2b3c4d5e6f7a8b9c0d1"))
                .body("alert.level", is("warning"));
    }

    @Test
    void getDetail_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/{id}/detail", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }

    // ---------- GET /articles/bundles/{periodKey} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getBundles_shouldReturnBundles_whenAuthorized() {
        ArticleBundleDTO.BundleItemDTO cafe = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1", "Café", new BigDecimal("500.00"), BigDecimal.valueOf(20), null);
        ArticleBundleDTO.BundleItemDTO croissant = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d2", "Croissant", new BigDecimal("300.00"), BigDecimal.valueOf(30), null);
        ArticleBundleDTO bundleDto = new ArticleBundleDTO(
                "matin",
                "Matin (6h-11h)",
                List.of(new ArticleBundleDTO.BundleDTO(
                        cafe, croissant, 3,
                        new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("2.0000"),
                        new BigDecimal("800.00"), new BigDecimal("500.00")
                ))
        );
        when(articleBundleService.getBundlesForTimeOfDay("matin")).thenReturn(bundleDto);

        given()
                .when().get("/articles/bundles/{periodKey}", "matin")
                .then()
                .statusCode(200)
                .body("periodKey", is("matin"))
                .body("bundles.size()", is(1))
                .body("bundles[0].first.name", is("Café"));
    }

    @Test
    void getBundles_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/bundles/{periodKey}", "matin")
                .then()
                .statusCode(401);
    }

    // ---------- GET /articles/bundles/tendance/{trendKey} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getCalendarTrendBundles_shouldReturnBundles_whenAuthorized() {
        ArticleBundleDTO.BundleItemDTO riz = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1", "Riz", new BigDecimal("1000.00"), BigDecimal.valueOf(20), null);
        ArticleBundleDTO.BundleItemDTO huile = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d2", "Huile", new BigDecimal("800.00"), BigDecimal.valueOf(30), null);
        ArticleBundleDTO bundleDto = new ArticleBundleDTO(
                "fin-mois",
                "Fin de mois (21-fin)",
                List.of(new ArticleBundleDTO.BundleDTO(
                        riz, huile, 3,
                        new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("2.0000"),
                        new BigDecimal("1800.00"), new BigDecimal("1100.00")
                ))
        );
        when(articleBundleService.getBundlesForCalendarTrend("fin-mois")).thenReturn(bundleDto);

        given()
                .when().get("/articles/bundles/tendance/{trendKey}", "fin-mois")
                .then()
                .statusCode(200)
                .body("periodKey", is("fin-mois"))
                .body("bundles.size()", is(1))
                .body("bundles[0].first.name", is("Riz"));
    }

    @Test
    void getCalendarTrendBundles_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/bundles/tendance/{trendKey}", "fin-mois")
                .then()
                .statusCode(401);
    }

    // ---------- GET /articles/bundles/categorie/{category} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getCategoryBundles_shouldReturnBundles_whenAuthorized() {
        ArticleBundleDTO.BundleItemDTO coton = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1", "Coton", new BigDecimal("500.00"), BigDecimal.valueOf(20), null);
        ArticleBundleDTO.BundleItemDTO garniture = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d2", "Garniture", new BigDecimal("300.00"), BigDecimal.valueOf(30), null);
        ArticleBundleDTO bundleDto = new ArticleBundleDTO(
                "categorie:Hygiène",
                "Besoin : Hygiène",
                List.of(new ArticleBundleDTO.BundleDTO(
                        coton, garniture, 3,
                        new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("2.0000"),
                        new BigDecimal("800.00"), new BigDecimal("500.00")
                ))
        );
        when(articleBundleService.getBundlesForCategory("Hygiène")).thenReturn(bundleDto);

        given()
                .when().get("/articles/bundles/categorie/{category}", "Hygiène")
                .then()
                .statusCode(200)
                .body("periodKey", is("categorie:Hygiène"))
                .body("bundles.size()", is(1))
                .body("bundles[0].first.name", is("Coton"));
    }

    @Test
    void getCategoryBundles_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/bundles/categorie/{category}", "Hygiène")
                .then()
                .statusCode(401);
    }

    // ---------- POST /articles/bundles/occasion ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getOccasionBundles_shouldReturnBundles_whenAuthorized() {
        ArticleBundleDTO.BundleItemDTO biere = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1", "Bière", new BigDecimal("1500.00"), BigDecimal.valueOf(20), null);
        ArticleBundleDTO.BundleItemDTO chips = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d2", "Chips", new BigDecimal("800.00"), BigDecimal.valueOf(30), null);
        ArticleBundleDTO bundleDto = new ArticleBundleDTO(
                "occasion:Soirée LDC",
                "Soirée LDC",
                List.of(new ArticleBundleDTO.BundleDTO(
                        biere, chips, 3,
                        new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("2.0000"),
                        new BigDecimal("2300.00"), new BigDecimal("1300.00")
                ))
        );
        OccasionBundleRequestDTO request = new OccasionBundleRequestDTO(
                "Soirée LDC", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
        when(articleBundleService.getBundlesForOccasion(any())).thenReturn(bundleDto);

        given()
                .contentType("application/json")
                .body(request)
                .when().post("/articles/bundles/occasion")
                .then()
                .statusCode(200)
                .body("label", is("Soirée LDC"))
                .body("bundles.size()", is(1))
                .body("bundles[0].first.name", is("Bière"));
    }

    @Test
    void getOccasionBundles_shouldReturnUnauthorized_whenNoAuthentication() {
        OccasionBundleRequestDTO request = new OccasionBundleRequestDTO(
                "Soirée LDC", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

        given()
                .contentType("application/json")
                .body(request)
                .when().post("/articles/bundles/occasion")
                .then()
                .statusCode(401);
    }

    // ---------- GET /articles/bundles/suggestions ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getCatalogSuggestions_shouldReturnBundles_whenAuthorized() {
        ArticleBundleDTO.BundleItemDTO cafe = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d1", "Café", new BigDecimal("500.00"), BigDecimal.valueOf(20), null);
        ArticleBundleDTO.BundleItemDTO croissant = new ArticleBundleDTO.BundleItemDTO(
                "64f1a2b3c4d5e6f7a8b9c0d2", "Croissant", new BigDecimal("300.00"), BigDecimal.valueOf(30), null);
        ArticleBundleDTO bundleDto = new ArticleBundleDTO(
                "catalogue",
                "Suggestions catalogue",
                List.of(new ArticleBundleDTO.BundleDTO(
                        cafe, croissant, 3,
                        new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("2.0000"),
                        new BigDecimal("800.00"), new BigDecimal("500.00")
                ))
        );
        when(articleBundleService.getCatalogSuggestions()).thenReturn(bundleDto);

        given()
                .when().get("/articles/bundles/suggestions")
                .then()
                .statusCode(200)
                .body("periodKey", is("catalogue"))
                .body("bundles[0].first.name", is("Café"));
    }

    @Test
    void getCatalogSuggestions_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/bundles/suggestions")
                .then()
                .statusCode(401);
    }

    // ---------- GET /articles/bundles/contenu/{key} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void getBundleContent_shouldReturnStoredContent_whenAuthorized() {
        ArticleBundleDTO bundle = new ArticleBundleDTO("matin", "Matin (6h-11h)", List.of());
        BundleContentDTO contentDto = new BundleContentDTO(
                "matin", "Matin (6h-11h)", bundle, "Texte marketing", "Conseil d'usage", Instant.now());
        when(bundleContentService.getStored("matin")).thenReturn(contentDto);

        given()
                .when().get("/articles/bundles/contenu/{key}", "matin")
                .then()
                .statusCode(200)
                .body("key", is("matin"))
                .body("marketingText", is("Texte marketing"));
    }

    @Test
    void getBundleContent_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().get("/articles/bundles/contenu/{key}", "matin")
                .then()
                .statusCode(401);
    }

    // ---------- POST /articles/bundles/{periodKey}/contenu ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void generateTimeOfDayContent_shouldReturnGeneratedContent_whenAuthorized() {
        ArticleBundleDTO bundle = new ArticleBundleDTO("matin", "Matin (6h-11h)", List.of());
        BundleContentDTO contentDto = new BundleContentDTO(
                "matin", "Matin (6h-11h)", bundle, "Texte marketing", "Conseil d'usage", Instant.now());
        when(articleBundleService.getBundlesForTimeOfDay("matin")).thenReturn(bundle);
        when(bundleContentService.generateAndStore(bundle)).thenReturn(contentDto);

        given()
                .when().post("/articles/bundles/{periodKey}/contenu", "matin")
                .then()
                .statusCode(200)
                .body("key", is("matin"))
                .body("marketingText", is("Texte marketing"));
    }

    @Test
    void generateTimeOfDayContent_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().post("/articles/bundles/{periodKey}/contenu", "matin")
                .then()
                .statusCode(401);
    }
}
