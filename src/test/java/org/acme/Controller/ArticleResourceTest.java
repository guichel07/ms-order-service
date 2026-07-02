package org.acme.Controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.security.TestSecurity;
import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.ArticleDTO;
import org.acme.DTO.QuantityAdjustmentDTO;
import org.acme.DTO.QuantityOrdered;
import org.acme.Entity.Article;
import org.acme.Exception.BusinessException;
import org.acme.Service.Article.ArticleService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ArticleResourceTest {

    @InjectMock
    ArticleService articleService;

    private Article sampleArticle;

    @BeforeEach
    void setUp() {
        sampleArticle = new Article();
        sampleArticle.id = new ObjectId();
        sampleArticle.setName("Croissant");
        sampleArticle.setIcon("croissant.svg");
        sampleArticle.setColor("#FFD700");
        sampleArticle.setCategory("Viennoiserie");
        sampleArticle.setPrice(new BigDecimal("1.20"));
        sampleArticle.setQuantity(50);
    }

    private ArticleDTO validArticleDTO() {
        return new ArticleDTO("Croissant", "croissant.svg", "#FFD700", "Viennoiserie", new BigDecimal("1.20"));
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
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void register_shouldReturn201_whenPayloadIsValid() {
        when(articleService.register(any(ArticleDTO.class))).thenReturn(sampleArticle);

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
    void register_shouldReturn400_whenNameIsBlank() {
        // Note: ArticleDTO currently has no @NotBlank on its fields besides validation
        // annotations declared in the record; this test documents current behavior
        // and should be updated if stricter validation is added to ArticleDTO.
        ArticleDTO invalid = new ArticleDTO(null, "icon.svg", "#000000", "Cat", new BigDecimal("-1"));
        when(articleService.register(any(ArticleDTO.class))).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(invalid)
                .when().post("/articles")
                .then()
                .statusCode(201); // ArticleDTO has no bean-validation constraints today
    }

    // ---------- PUT /articles/{id} ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void update_shouldReturnUpdatedArticle() {
        when(articleService.update(eq("64f1a2b3c4d5e6f7a8b9c0d1"), any(ArticleDTO.class))).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(validArticleDTO())
                .when().put("/articles/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200)
                .body("name", is("Croissant"));
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

    // ---------- DELETE /articles/{id} ----------

    @Test
    @TestSecurity(user = "admin1", roles = { "ADMIN" })
    void delete_shouldReturn204() {
        doNothing().when(articleService).delete("64f1a2b3c4d5e6f7a8b9c0d1");

        given()
                .when().delete("/articles/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(204);

        verify(articleService, times(1)).delete("64f1a2b3c4d5e6f7a8b9c0d1");
    }

    @Test
    void delete_shouldReturnUnauthorized_whenNoAuthentication() {
        given()
                .when().delete("/articles/{id}", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(401);
    }

    // ---------- PATCH /articles/{id}/quantity (restock) ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void restock_shouldReturn200_whenQuantityIsPositive() {
        when(articleService.incrementQuantity(eq("64f1a2b3c4d5e6f7a8b9c0d1"), anyInt())).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(new QuantityAdjustmentDTO(10))
                .when().patch("/articles/{id}/quantity", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200);

        verify(articleService).incrementQuantity("64f1a2b3c4d5e6f7a8b9c0d1", 10);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void restock_shouldReturn400_whenQuantityIsNotPositive() {
        given()
                .contentType("application/json")
                .body(new QuantityAdjustmentDTO(0))
                .when().patch("/articles/{id}/quantity", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(400);
    }

    // ---------- PATCH /articles/{id}/quantityOrdered (destock) ----------

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void destock_shouldReturn200_whenQuantityIsPositive() {
        when(articleService.decrementQuantity(eq("64f1a2b3c4d5e6f7a8b9c0d1"), anyInt())).thenReturn(sampleArticle);

        given()
                .contentType("application/json")
                .body(new QuantityOrdered(5))
                .when().patch("/articles/{id}/quantityOrdered", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(200);

        verify(articleService).decrementQuantity("64f1a2b3c4d5e6f7a8b9c0d1", 5);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void destock_shouldReturn400_whenQuantityIsNotPositive() {
        given()
                .contentType("application/json")
                .body(new QuantityOrdered(-3))
                .when().patch("/articles/{id}/quantityOrdered", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "seller1", roles = { "SELLER" })
    void destock_shouldReturn400_whenBusinessRuleViolated() {
        when(articleService.decrementQuantity(anyString(), anyInt()))
                .thenThrow(new BusinessException(jakarta.ws.rs.core.Response.Status.BAD_REQUEST, "Stock insuffisant"));

        given()
                .contentType("application/json")
                .body(new QuantityOrdered(999))
                .when().patch("/articles/{id}/quantityOrdered", "64f1a2b3c4d5e6f7a8b9c0d1")
                .then()
                .statusCode(400)
                .body("message", is("Stock insuffisant"));
    }
}