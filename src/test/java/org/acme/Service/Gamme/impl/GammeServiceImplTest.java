package org.acme.Service.Gamme.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.GammeItemRequestDTO;
import org.acme.DTO.GammeRequestDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Gamme;
import org.acme.Entity.GammeItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.GammeRepository;
import org.acme.Service.Ai.AiContentClient;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link GammeServiceImpl}.
 * GammeRepository, ArticleRepository et AiContentClient sont mockés — aucune base de données ni
 * appel IA réel nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class GammeServiceImplTest {

    @Mock
    private GammeRepository gammeRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private AiContentClient aiContentClient;

    @InjectMocks
    private GammeServiceImpl gammeService;

    private Article article(String name, String icon, long price, long costPrice, int stock) {
        Article a = new Article();
        a.id = new ObjectId();
        a.setName(name);
        a.setIcon(icon);
        a.setPrice(BigDecimal.valueOf(price));
        a.setCostPrice(BigDecimal.valueOf(costPrice));
        a.setQuantity(BigDecimal.valueOf(stock));
        a.setArchived(false);
        return a;
    }

    @Test
    void create_persistsGamme_andReturnsEnrichedLines_withRealMarginFromCostPrice() {
        Article pain = article("Pain", "🍞", 500, 200, 40);
        Article lait = article("Lait", "🥛", 800, 300, 25);
        when(articleRepository.findById(pain.id)).thenReturn(pain, pain);
        when(articleRepository.findById(lait.id)).thenReturn(lait, lait);
        doAnswer(invocation -> {
            Gamme persisted = invocation.getArgument(0);
            persisted.id = new ObjectId();
            return null;
        }).when(gammeRepository).persist(any(Gamme.class));

        GammeRequestDTO request = new GammeRequestDTO(
            "Petit-déjeuner",
            List.of(
                new GammeItemRequestDTO(pain.id.toHexString(), BigDecimal.valueOf(500), 2),
                new GammeItemRequestDTO(lait.id.toHexString(), BigDecimal.valueOf(800), 1)
            )
        );

        GammeResponseDTO result = gammeService.create(request);

        assertEquals("Petit-déjeuner", result.title());
        assertEquals(2, result.items().size());
        // Pain : prix 500 x2 = 1000, marge (500-200)x2 = 600.
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.items().get(0).lineTotal()));
        assertEquals(0, new BigDecimal("600.00").compareTo(result.items().get(0).lineMargin()));
        // Lait : prix 800 x1 = 800, marge (800-300)x1 = 500.
        assertEquals(0, new BigDecimal("800.00").compareTo(result.items().get(1).lineTotal()));
        assertEquals(0, new BigDecimal("500.00").compareTo(result.items().get(1).lineMargin()));
        // Totaux.
        assertEquals(0, new BigDecimal("1800.00").compareTo(result.totalPrice()));
        assertEquals(0, new BigDecimal("1100.00").compareTo(result.totalMargin()));
    }

    @Test
    void create_throwsBadRequest_whenAnArticleDoesNotExist() {
        Article pain = article("Pain", "🍞", 500, 200, 40);
        String unknownId = new ObjectId().toHexString();
        when(articleRepository.findById(pain.id)).thenReturn(pain);
        when(articleRepository.findById(new ObjectId(unknownId))).thenReturn(null);

        GammeRequestDTO request = new GammeRequestDTO(
            "Petit-déjeuner",
            List.of(
                new GammeItemRequestDTO(pain.id.toHexString(), BigDecimal.valueOf(500), 1),
                new GammeItemRequestDTO(unknownId, BigDecimal.valueOf(300), 1)
            )
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> gammeService.create(request)
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void findById_throwsNotFound_whenGammeDoesNotExist() {
        String id = new ObjectId().toHexString();
        when(gammeRepository.findById(new ObjectId(id))).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> gammeService.findById(id)
        );

        assertEquals(404, exception.getErrorCode().getStatusCode());
    }

    @Test
    void update_replacesItems_whenGammeExists() {
        Gamme existing = new Gamme();
        existing.id = new ObjectId();
        existing.setTitle("Ancien titre");
        existing.setItems(List.of(new GammeItem("old-article-id", BigDecimal.TEN, 1)));
        when(gammeRepository.findById(existing.id)).thenReturn(existing);

        Article sucre = article("Sucre", "🍬", 300, 100, 60);
        Article the = article("Thé", "🍵", 400, 150, 30);
        when(articleRepository.findById(sucre.id)).thenReturn(sucre);
        when(articleRepository.findById(the.id)).thenReturn(the, the);

        GammeRequestDTO request = new GammeRequestDTO(
            "Nouveau titre",
            List.of(
                new GammeItemRequestDTO(sucre.id.toHexString(), BigDecimal.valueOf(300), 1),
                new GammeItemRequestDTO(the.id.toHexString(), BigDecimal.valueOf(400), 1)
            )
        );

        GammeResponseDTO result = gammeService.update(existing.id.toHexString(), request);

        assertEquals("Nouveau titre", result.title());
        assertEquals(2, result.items().size());
        verify(gammeRepository).update(existing);
    }

    @Test
    void listAll_returnsAllStoredGammes_enrichedWithCurrentArticleData() {
        Article pain = article("Pain", "🍞", 500, 200, 40);
        when(articleRepository.findById(pain.id)).thenReturn(pain, pain, pain, pain);

        Gamme g1 = new Gamme();
        g1.id = new ObjectId();
        g1.setTitle("Petit-déjeuner");
        g1.setItems(List.of(new GammeItem(pain.id.toHexString(), BigDecimal.valueOf(500), 2)));
        Gamme g2 = new Gamme();
        g2.id = new ObjectId();
        g2.setTitle("Menu léger");
        g2.setItems(List.of(new GammeItem(pain.id.toHexString(), BigDecimal.valueOf(500), 1)));
        when(gammeRepository.listAll()).thenReturn(List.of(g1, g2));

        List<GammeResponseDTO> result = gammeService.listAll();

        assertEquals(2, result.size());
        assertEquals("Petit-déjeuner", result.get(0).title());
        assertEquals("Menu léger", result.get(1).title());
    }

    @Test
    void toResponseDTO_skipsLine_whenArticleWasDeletedSinceCreation() {
        Gamme gamme = new Gamme();
        gamme.id = new ObjectId();
        gamme.setTitle("Gamme orpheline");
        String deletedArticleId = new ObjectId().toHexString();
        gamme.setItems(List.of(new GammeItem(deletedArticleId, BigDecimal.TEN, 1)));
        when(gammeRepository.findById(gamme.id)).thenReturn(gamme);
        when(articleRepository.findById(new ObjectId(deletedArticleId))).thenReturn(null);

        GammeResponseDTO result = gammeService.findById(gamme.id.toHexString());

        assertEquals(0, result.items().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalPrice()));
    }

    @Test
    void generateContent_callsAiClient_andPersistsMarketingTextAndPattern() {
        Article pain = article("Pain", "🍞", 500, 200, 40);
        when(articleRepository.findById(pain.id)).thenReturn(pain, pain, pain);

        Gamme gamme = new Gamme();
        gamme.id = new ObjectId();
        gamme.setTitle("Petit-déjeuner");
        gamme.setItems(List.of(new GammeItem(pain.id.toHexString(), BigDecimal.valueOf(500), 2)));
        when(gammeRepository.findById(gamme.id)).thenReturn(gamme);
        when(aiContentClient.generateForGamme(any())).thenReturn(
            new AiGeneratedContentDTO("Texte marketing", "Pattern reconnu")
        );

        GammeResponseDTO result = gammeService.generateContent(gamme.id.toHexString());

        assertEquals("Texte marketing", result.marketingText());
        assertEquals("Pattern reconnu", result.patternDescription());
        assertEquals("Texte marketing", gamme.getMarketingText());
        verify(gammeRepository).update(gamme);
    }
}
