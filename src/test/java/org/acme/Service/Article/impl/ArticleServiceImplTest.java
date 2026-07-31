package org.acme.Service.Article.impl;

import jakarta.ws.rs.core.Response;
import org.acme.DTO.ArticleRequestDTO;
import org.acme.DTO.ArticleResponseDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

    private ArticleRequestDTO buildArticleDTO(String name) {
        return buildArticleDTO(name, "icon.png", "#FF0000", "Boissons");
    }

    private ArticleRequestDTO buildArticleDTO(String name, String icon, String color, String category) {
        return new ArticleRequestDTO(
                name,
                icon,
                color,
                category,
                BigDecimal.valueOf(2.5),
                BigDecimal.valueOf(2.5),
                BigDecimal.valueOf(2.5),
                BigDecimal.valueOf(2.5),
                BigDecimal.valueOf(2.5),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                10,
                5,
                false,
                false,
                null
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

        List<ArticleResponseDTO> result = articleService.listAll();

        assertEquals(2, result.size());
    }

    // ---------------------------------------------------------------
    // findByName
    // ---------------------------------------------------------------

    @Test
    void findByName_shouldReturnArticle_whenItExists() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findByName("Café")).thenReturn(article);

        ArticleResponseDTO result = articleService.findByName("Café");

        assertNotNull(result);
        assertEquals("Café", result.name());
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

        ArticleResponseDTO result = articleService.findById(article.id.toHexString());

        assertNotNull(result);
        assertEquals("Café", result.name());
        assertEquals(article.id.toHexString(), result.id());
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
        ArticleRequestDTO dto = buildArticleDTO("Café");
        when(articleRepository.count("name", "Café")).thenReturn(0L);
        // Panache assigne l'id au moment du persist() réel ; on simule ce comportement,
        // le mock ne le ferait pas tout seul.
        doAnswer(invocation -> {
            Article persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(articleRepository).persist(any(Article.class));

        ArticleResponseDTO result = articleService.register(dto);

        assertNotNull(result);
        assertEquals("Café", result.name());
        // register() respecte le stock initial soumis (le formulaire de création en a un) —
        // buildArticleDTO() en soumet 10.
        assertEquals(10, result.quantity());
        verify(articleRepository, times(1)).persist(any(Article.class));
    }

    @Test
    void register_shouldThrowConflict_whenNameAlreadyUsed() {
        ArticleRequestDTO dto = buildArticleDTO("Café");
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

        ArticleRequestDTO dto = buildArticleDTO("Café");

        assertThrows(
                BusinessException.class,
                () -> articleService.update(missingId.toHexString(), dto)
        );
    }

    @Test
    void update_shouldUpdateFields_whenNameIsUnchanged() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleRequestDTO dto = buildArticleDTO("Café", "new-icon.png", "#00FF00", "Snacks");

        ArticleResponseDTO result = articleService.update(article.id.toHexString(), dto);

        assertEquals("new-icon.png", result.icon());
        assertEquals("#00FF00", result.color());
        assertEquals("Snacks", result.category());
        assertEquals(BigDecimal.valueOf(2.5), result.price());
        verify(articleRepository, times(1)).update(article);
    }

    @Test
    void register_shouldComputeCostPriceFromPurchaseBreakdown_ignoringAnyCostSentDirectly() {
        ArticleRequestDTO dto = new ArticleRequestDTO(
                "Café", "icon.png", "#FF0000", "Boissons",
                BigDecimal.valueOf(5), BigDecimal.valueOf(3), BigDecimal.valueOf(8), BigDecimal.valueOf(5),
                BigDecimal.valueOf(9000), BigDecimal.valueOf(500), BigDecimal.valueOf(500),
                10, 10, 5, false, false, null
        );
        when(articleRepository.count("name", "Café")).thenReturn(0L);
        doAnswer(invocation -> {
            Article persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(articleRepository).persist(any(Article.class));

        ArticleResponseDTO result = articleService.register(dto);

        // (9000 + 500 + 500) / 10 = 1000 — jamais un costPrice envoyé directement (il n'existe
        // même plus comme champ d'entrée sur ArticleRequestDTO).
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(result.costPrice()));
    }

    @Test
    void update_shouldRaiseWarningReview_whenArticleWasLockedAndCostJumpsPastThreshold() {
        Article article = buildArticle("Café", 10);
        article.setLocked(true);
        article.setCostPrice(BigDecimal.valueOf(1000));
        article.setPrice(BigDecimal.valueOf(1500));
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleRequestDTO dto = new ArticleRequestDTO(
                "Café", "icon.png", "#FF0000", "Boissons",
                BigDecimal.valueOf(1500), BigDecimal.valueOf(1500), BigDecimal.valueOf(1500), BigDecimal.valueOf(1500),
                BigDecimal.valueOf(13000), BigDecimal.ZERO, BigDecimal.ZERO,
                10, 10, 5, true, false, null
        );

        ArticleResponseDTO result = articleService.update(article.id.toHexString(), dto);

        // Nouveau coût 1300 vs ancien 1000 -> +30%, au-delà du seuil de 5%.
        assertNotNull(result.priceReview());
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(result.priceReview().previousPrice()));
        assertEquals(0, BigDecimal.valueOf(1300).compareTo(result.priceReview().newUnitCost()));
        assertEquals(0, BigDecimal.valueOf(300).compareTo(result.priceReview().marginDrop()));
    }

    @Test
    void update_shouldNotRaiseReview_whenArticleWasNotLocked_evenIfCostJumps() {
        Article article = buildArticle("Café", 10);
        article.setLocked(false);
        article.setCostPrice(BigDecimal.valueOf(1000));
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleRequestDTO dto = new ArticleRequestDTO(
                "Café", "icon.png", "#FF0000", "Boissons",
                BigDecimal.valueOf(1500), BigDecimal.valueOf(1500), BigDecimal.valueOf(1500), BigDecimal.valueOf(1500),
                BigDecimal.valueOf(13000), BigDecimal.ZERO, BigDecimal.ZERO,
                10, 10, 5, false, false, null
        );

        ArticleResponseDTO result = articleService.update(article.id.toHexString(), dto);

        assertNull(result.priceReview());
    }

    @Test
    void update_shouldNotRaiseReview_whenCostChangeStaysUnderThreshold() {
        Article article = buildArticle("Café", 10);
        article.setLocked(true);
        article.setCostPrice(BigDecimal.valueOf(1000));
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleRequestDTO dto = new ArticleRequestDTO(
                "Café", "icon.png", "#FF0000", "Boissons",
                BigDecimal.valueOf(1500), BigDecimal.valueOf(1500), BigDecimal.valueOf(1500), BigDecimal.valueOf(1500),
                BigDecimal.valueOf(10200), BigDecimal.ZERO, BigDecimal.ZERO,
                10, 10, 5, true, false, null
        );

        ArticleResponseDTO result = articleService.update(article.id.toHexString(), dto);

        // 1020 vs 1000 -> +2%, sous le seuil de 5%.
        assertNull(result.priceReview());
    }

    @Test
    void update_shouldThrowConflict_whenRenamingToAnExistingName() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);
        when(articleRepository.count("name", "Thé")).thenReturn(1L);

        ArticleRequestDTO dto = buildArticleDTO("Thé");

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

        ArticleRequestDTO dto = buildArticleDTO("Chocolat");

        ArticleResponseDTO result = articleService.update(article.id.toHexString(), dto);

        assertEquals("Chocolat", result.name());
    }

    // ---------------------------------------------------------------
    // toggleArchive
    // ---------------------------------------------------------------

    @Test
    void toggleArchive_shouldArchiveArticle_whenItWasActive() {
        Article article = buildArticle("Café", 10);
        article.setArchived(false);
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleResponseDTO result = articleService.toggleArchive(article.id.toHexString());

        assertTrue(result.archived());
        verify(articleRepository, times(1)).update(article);
    }

    @Test
    void toggleArchive_shouldUnarchiveArticle_whenItWasArchived() {
        Article article = buildArticle("Café", 10);
        article.setArchived(true);
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleResponseDTO result = articleService.toggleArchive(article.id.toHexString());

        assertFalse(result.archived());
    }

    @Test
    void toggleArchive_shouldThrowNotFound_whenArticleDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(articleRepository.findById(missingId)).thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> articleService.toggleArchive(missingId.toHexString())
        );
    }

    // ---------------------------------------------------------------
    // decrementQuantity
    // ---------------------------------------------------------------

    @Test
    void decrementQuantity_shouldReduceStock_whenArticleExists() {
        Article article = buildArticle("Café", 10);
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleResponseDTO result = articleService.decrementQuantity(article.id.toHexString(), 3);

        assertEquals(7, result.quantity());
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

        ArticleResponseDTO result = articleService.incrementQuantity(article.id.toHexString(), 5);

        assertEquals(15, result.quantity());
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

    // ---------------------------------------------------------------
    // receiveStock
    // ---------------------------------------------------------------

    @Test
    void receiveStock_addsQtyAndReplacesCostBasis_withTheNewLot() {
        Article article = buildArticle("Riz parfumé 5kg", 10);
        article.setPurchasePrice(BigDecimal.valueOf(2000));
        article.setTransport(BigDecimal.valueOf(100));
        article.setPurchasedQuantity(5);
        article.setCostPrice(BigDecimal.valueOf(420));
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleResponseDTO result = articleService.receiveStock(
            article.id.toHexString(), 20, BigDecimal.valueOf(6000), BigDecimal.valueOf(400)
        );

        assertEquals(30, result.quantity());
        assertEquals(0, BigDecimal.valueOf(6000).compareTo(result.purchasePrice()));
        assertEquals(0, BigDecimal.valueOf(400).compareTo(result.transport()));
        assertEquals(20, result.purchasedQuantity());
        // (6000 + 400) / 20 = 320
        assertEquals(0, new BigDecimal("320.0000").compareTo(result.costPrice()));
        verify(articleRepository).update(article);
    }

    @Test
    void receiveStock_throwsNotFound_whenArticleDoesNotExist() {
        ObjectId missingId = new ObjectId();
        when(articleRepository.findById(missingId)).thenReturn(null);

        assertThrows(
            BusinessException.class,
            () -> articleService.receiveStock(missingId.toHexString(), 5, BigDecimal.valueOf(100), BigDecimal.ZERO)
        );
    }

    @Test
    void receiveStock_flagsPriceReview_whenArticleIsLockedAndCostJumpsSignificantly() {
        Article article = buildArticle("Riz parfumé 5kg", 10);
        article.setLocked(true);
        article.setPrice(BigDecimal.valueOf(1000));
        article.setPurchasedQuantity(5);
        article.setCostPrice(BigDecimal.valueOf(100));
        when(articleRepository.findById(article.id)).thenReturn(article);

        // nouveau coût (5000+0)/10 = 500, largement au-dessus du seuil de 5% par rapport à 100
        ArticleResponseDTO result = articleService.receiveStock(
            article.id.toHexString(), 10, BigDecimal.valueOf(5000), BigDecimal.ZERO
        );

        assertNotNull(result.priceReview());
    }

    @Test
    void receiveStock_flagsPriceReview_evenWhenArticleIsNotLocked() {
        // Systématique : la revue de prix ne dépend plus du verrouillage de l'article.
        Article article = buildArticle("Riz parfumé 5kg", 10);
        article.setLocked(false);
        article.setPrice(BigDecimal.valueOf(1000));
        article.setPurchasedQuantity(5);
        article.setCostPrice(BigDecimal.valueOf(100));
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleResponseDTO result = articleService.receiveStock(
            article.id.toHexString(), 10, BigDecimal.valueOf(5000), BigDecimal.ZERO
        );

        assertNotNull(result.priceReview());
    }

    @Test
    void receiveStock_flagsPriceReview_evenForATinyCostChange() {
        // Systématique : plus de seuil de 5%, tout changement de coût doit remonter.
        Article article = buildArticle("Riz parfumé 5kg", 10);
        article.setPrice(BigDecimal.valueOf(1000));
        article.setPurchasedQuantity(5);
        article.setCostPrice(BigDecimal.valueOf(500));
        when(articleRepository.findById(article.id)).thenReturn(article);

        // nouveau coût (5010+0)/10 = 501, soit +0.2% seulement par rapport à 500
        ArticleResponseDTO result = articleService.receiveStock(
            article.id.toHexString(), 10, BigDecimal.valueOf(5010), BigDecimal.ZERO
        );

        assertNotNull(result.priceReview());
    }

    @Test
    void receiveStock_flagsPriceReview_onTheVeryFirstPurchase_whenThereIsNoPreviousCost() {
        // Un article jamais acheté avant (costPrice null) compte comme un coût de 0 :
        // son tout premier achat réel doit donc aussi déclencher la revue, pas être sauté en silence.
        Article article = buildArticle("Riz parfumé 5kg", 0);
        article.setPrice(BigDecimal.valueOf(1000));
        when(articleRepository.findById(article.id)).thenReturn(article);

        ArticleResponseDTO result = articleService.receiveStock(
            article.id.toHexString(), 10, BigDecimal.valueOf(5000), BigDecimal.ZERO
        );

        assertNotNull(result.priceReview());
    }

    @Test
    void receiveStock_doesNotFlagPriceReview_whenCostIsStrictlyUnchanged() {
        Article article = buildArticle("Riz parfumé 5kg", 10);
        article.setPrice(BigDecimal.valueOf(1000));
        article.setPurchasedQuantity(5);
        article.setCostPrice(BigDecimal.valueOf(500));
        when(articleRepository.findById(article.id)).thenReturn(article);

        // nouveau coût (5000+0)/10 = 500, identique au coût précédent
        ArticleResponseDTO result = articleService.receiveStock(
            article.id.toHexString(), 10, BigDecimal.valueOf(5000), BigDecimal.ZERO
        );

        assertNull(result.priceReview());
    }
}
