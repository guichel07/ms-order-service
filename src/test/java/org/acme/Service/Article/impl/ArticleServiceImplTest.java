package org.acme.Service.Article.impl;

import jakarta.ws.rs.core.Response;
import org.acme.DTO.ArticleDTO;
import org.acme.Entity.Article;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link ArticleServiceImpl}.
 * Le repository Mongo Panache est mocké : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleServiceImpl articleService;

    private Article buildArticle(String name, int quantity) {
        Article article = new Article();
        article.id = new ObjectId();
        article.setName(name);
        article.setIcon("icon.png");
        article.setColor("#FF0000");
        article.setCategory("Boissons");
        article.setPrice(BigDecimal.valueOf(2.5));
        article.setQuantity(quantity);
        return article;
    }

    private ArticleDTO buildArticleDTO(String name) {
        return new ArticleDTO(
                name,
                "icon.png",
                "#FF0000",
                "Boissons",
                BigDecimal.valueOf(2.5)
        );
    }

    // ---------------------------------------------------------------
    // listAll
    // ---------------------------------------------------------------

    @Test
    void listAll_shouldReturnAllArticles() {
        List<Article> articles = List.of(
                buildArticle("Café", 10),
                buildArticle("Thé", 5)
        );
        when(articleRepository.listAll()).thenReturn(articles);

        List<Article> result = articleService.listAll();

        assertEquals(2, result.size());
    }

    // ---------------------------------------------------------------
    // findByName
    // ---------------------------------------------------------------

    @Test
    void findByName_shouldReturnArticle_whenItExists() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findByName("Café")).thenReturn(article);

        Article result = articleService.findByName("Café");

        assertNotNull(result);
        assertEquals("Café", result.getName());
    }

    @Test
    void findByName_shouldThrowNotFound_whenItDoesNotExist() {
        when(articleRepository.findByName("Inconnu")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> articleService.findByName("Inconnu")
        );

        assertEquals(Response.Status.NOT_FOUND, exception.getErrorCode());
    }

    // ---------------------------------------------------------------
    // findById
    // ---------------------------------------------------------------

    @Test
    void findById_shouldReturnArticle_whenItExists() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        Article result = articleService.findById(article.id.toHexString());

        assertNotNull(result);
        assertEquals("Café", result.getName());
    }

    @Test
    void findById_shouldThrowNotFound_whenItDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(articleRepository.findById(missingId)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> articleService.findById(missingId.toHexString())
        );

        assertEquals(Response.Status.NOT_FOUND, exception.getErrorCode());
    }

    // ---------------------------------------------------------------
    // register
    // ---------------------------------------------------------------

    @Test
    void register_shouldPersistNewArticle_whenNameIsFree() {
        ArticleDTO dto = buildArticleDTO("Café");
        when(articleRepository.count("name", "Café")).thenReturn(0L);

        Article result = articleService.register(dto);

        assertNotNull(result);
        assertEquals("Café", result.getName());
        assertEquals(0, result.getQuantity());
        verify(articleRepository, times(1)).persist(result);
    }

    @Test
    void register_shouldThrowConflict_whenNameAlreadyUsed() {
        ArticleDTO dto = buildArticleDTO("Café");
        when(articleRepository.count("name", "Café")).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> articleService.register(dto)
        );

        assertEquals(Response.Status.CONFLICT, exception.getErrorCode());
        verify(articleRepository, never()).persist(any(Article.class));
    }

    // ---------------------------------------------------------------
    // update
    // ---------------------------------------------------------------

    @Test
    void update_shouldThrowNotFound_whenArticleDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(articleRepository.findById(missingId)).thenReturn(null);

        ArticleDTO dto = buildArticleDTO("Café");

        assertThrows(
                BusinessException.class,
                () -> articleService.update(missingId.toHexString(), dto)
        );
    }

    @Test
    void update_shouldUpdateFields_whenNameIsUnchanged() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleDTO dto = new ArticleDTO(
                "Café",
                "new-icon.png",
                "#00FF00",
                "Snacks",
                BigDecimal.valueOf(3.0)
        );

        Article result = articleService.update(article.id.toHexString(), dto);

        assertEquals("new-icon.png", result.getIcon());
        assertEquals("#00FF00", result.getColor());
        assertEquals("Snacks", result.getCategory());
        assertEquals(BigDecimal.valueOf(3.0), result.getPrice());
        verify(articleRepository, times(1)).update(article);
    }

    @Test
    void update_shouldThrowConflict_whenRenamingToAnExistingName() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);
        when(articleRepository.count("name", "Thé")).thenReturn(1L);

        ArticleDTO dto = buildArticleDTO("Thé");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> articleService.update(article.id.toHexString(), dto)
        );

        assertEquals(Response.Status.CONFLICT, exception.getErrorCode());
        verify(articleRepository, never()).update(any(Article.class));
    }

    @Test
    void update_shouldSucceed_whenRenamingToAFreeName() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);
        when(articleRepository.count("name", "Chocolat")).thenReturn(0L);

        ArticleDTO dto = buildArticleDTO("Chocolat");

        Article result = articleService.update(article.id.toHexString(), dto);

        assertEquals("Chocolat", result.getName());
    }

    // ---------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------

    @Test
    void delete_shouldRemoveArticle_whenItExists() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        articleService.delete(article.id.toHexString());

        verify(articleRepository, times(1)).delete(article);
    }

    @Test
    void delete_shouldThrowNotFound_whenArticleDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(articleRepository.findById(missingId)).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> articleService.delete(missingId.toHexString())
        );
    }

    // ---------------------------------------------------------------
    // decrementQuantity
    // ---------------------------------------------------------------

    @Test
    void decrementQuantity_shouldReduceStock_whenArticleExists() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        Article result = articleService.decrementQuantity(article.id.toHexString(), 3);

        assertEquals(7, result.getQuantity());
        verify(articleRepository, times(1)).update(article);
    }

    @Test
    void decrementQuantity_shouldThrowNotFound_whenArticleDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(articleRepository.findById(missingId)).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> articleService.decrementQuantity(missingId.toHexString(), 1)
        );
    }

    // ---------------------------------------------------------------
    // incrementQuantity
    // ---------------------------------------------------------------

    @Test
    void incrementQuantity_shouldIncreaseStock_whenArticleExists() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        Article result = articleService.incrementQuantity(article.id.toHexString(), 5);

        assertEquals(15, result.getQuantity());
        verify(articleRepository, times(1)).update(article);
    }

    @Test
    void incrementQuantity_shouldThrowNotFound_whenArticleDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(articleRepository.findById(missingId)).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> articleService.incrementQuantity(missingId.toHexString(), 5)
        );
    }

    @Test
    void incrementQuantity_shouldThrowBadRequest_whenQuantityIsNotPositive() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> articleService.incrementQuantity(article.id.toHexString(), 0)
        );

        assertEquals(Response.Status.BAD_REQUEST, exception.getErrorCode());
        verify(articleRepository, never()).update(any(Article.class));
    }
}