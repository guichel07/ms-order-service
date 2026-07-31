package org.acme.Service.Ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.GammeItemResponseDTO;
import org.acme.DTO.GammeResponseDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sans clé API configurée (le cas par défaut en dev/CI), AnthropicAiContentClient ne doit jamais
 * tenter d'appel réseau — il se rabat directement sur le texte gabarit. Le vrai appel à Claude
 * n'est pas testé ici (nécessiterait une clé réelle et un appel réseau sortant).
 */
class AnthropicAiContentClientTest {

    private final AnthropicAiContentClient client = new AnthropicAiContentClient(
        Optional.empty(), "claude-sonnet-5", new ObjectMapper()
    );

    @Test
    void generate_fallsBackToStubText_whenNoApiKeyConfigured() {
        ArticleBundleDTO.BundleItemDTO first = new ArticleBundleDTO.BundleItemDTO(
            "a", "Café", BigDecimal.valueOf(500), 20, null
        );
        ArticleBundleDTO.BundleItemDTO second = new ArticleBundleDTO.BundleItemDTO(
            "b", "Croissant", BigDecimal.valueOf(300), 20, null
        );
        ArticleBundleDTO.BundleDTO topBundle = new ArticleBundleDTO.BundleDTO(
            first, second, 3, BigDecimal.valueOf(50), BigDecimal.valueOf(100),
            BigDecimal.valueOf(2), BigDecimal.valueOf(800), BigDecimal.valueOf(500)
        );
        ArticleBundleDTO bundle = new ArticleBundleDTO("matin", "Matin (6h-11h)", List.of(topBundle));

        AiGeneratedContentDTO result = client.generate(bundle);

        assertTrue(result.marketingText().contains("Café"));
        assertTrue(result.marketingText().contains("Croissant"));
    }

    @Test
    void generate_fallsBackToStubText_whenBundleHasNoEntries() {
        ArticleBundleDTO bundle = new ArticleBundleDTO("matin", "Matin (6h-11h)", List.of());

        AiGeneratedContentDTO result = client.generate(bundle);

        assertTrue(result.marketingText().contains("Pas assez de données"));
        assertNull(result.usageTip());
    }

    @Test
    void generateForGamme_fallsBackToStubText_whenNoApiKeyConfigured() {
        GammeItemResponseDTO item = new GammeItemResponseDTO(
            "a", "Pain", "🍞", new BigDecimal("500.00"), 2, new BigDecimal("200.00"), 40,
            new BigDecimal("1000.00"), new BigDecimal("600.00")
        );
        GammeResponseDTO gamme = new GammeResponseDTO(
            "g1", "Petit-déjeuner", List.of(item),
            new BigDecimal("1000.00"), new BigDecimal("600.00"),
            null, null, null, Instant.now(), Instant.now()
        );

        AiGeneratedContentDTO result = client.generateForGamme(gamme);

        assertTrue(result.marketingText().contains("Petit-déjeuner"));
        assertTrue(result.marketingText().contains("Pain"));
    }

    @Test
    void generateForGamme_fallsBackToStubText_whenGammeHasNoItems() {
        GammeResponseDTO gamme = new GammeResponseDTO(
            "g1", "Gamme vide", List.of(),
            BigDecimal.ZERO, BigDecimal.ZERO,
            null, null, null, Instant.now(), Instant.now()
        );

        AiGeneratedContentDTO result = client.generateForGamme(gamme);

        assertEquals(
            "Ajoute au moins un article pour générer le texte de \"Gamme vide\".",
            result.marketingText()
        );
    }
}
