package org.acme.Service.Ai.impl;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.ArticleBundleDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubAiContentClientTest {

    private final StubAiContentClient client = new StubAiContentClient();

    private ArticleBundleDTO.BundleItemDTO item(String name, long price) {
        return new ArticleBundleDTO.BundleItemDTO(
            name.toLowerCase(), name, BigDecimal.valueOf(price), 20, null
        );
    }

    @Test
    void generate_returnsFallbackMessage_whenNoBundlesAvailable() {
        ArticleBundleDTO bundle = new ArticleBundleDTO("matin", "Matin (6h-11h)", List.of());

        AiGeneratedContentDTO result = client.generate(bundle);

        assertTrue(result.marketingText().contains("Pas assez de données"));
        assertTrue(result.marketingText().contains("Matin (6h-11h)"));
        assertNull(result.usageTip());
    }

    @Test
    void generate_mentionsTopBundleProducts_inMarketingTextAndUsageTip() {
        ArticleBundleDTO.BundleDTO topBundle = new ArticleBundleDTO.BundleDTO(
            item("Café", 500), item("Croissant", 300), 3,
            BigDecimal.valueOf(50), BigDecimal.valueOf(100), BigDecimal.valueOf(2),
            BigDecimal.valueOf(800), BigDecimal.valueOf(500)
        );
        ArticleBundleDTO bundle = new ArticleBundleDTO("matin", "Matin (6h-11h)", List.of(topBundle));

        AiGeneratedContentDTO result = client.generate(bundle);

        assertTrue(result.marketingText().contains("Café"));
        assertTrue(result.marketingText().contains("Croissant"));
        assertTrue(result.marketingText().contains("800"));
        assertTrue(result.usageTip().contains("Café"));
        assertTrue(result.usageTip().contains("Croissant"));
    }
}
