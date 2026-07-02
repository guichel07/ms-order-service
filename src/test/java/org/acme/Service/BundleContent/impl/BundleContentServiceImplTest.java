package org.acme.Service.BundleContent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.BundleContentDTO;
import org.acme.Entity.BundleContent;
import org.acme.Exception.BusinessException;
import org.acme.Repository.BundleContentRepository;
import org.acme.Service.Ai.AiContentClient;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link BundleContentServiceImpl}.
 * BundleContentRepository et AiContentClient sont mockés — un vrai ObjectMapper (avec le module
 * JSR310) est utilisé pour vérifier la sérialisation/désérialisation réelle du bundle stocké.
 */
@ExtendWith(MockitoExtension.class)
class BundleContentServiceImplTest {

    @Mock
    private BundleContentRepository bundleContentRepository;

    @Mock
    private AiContentClient aiContentClient;

    private ObjectMapper objectMapper;
    private BundleContentServiceImpl bundleContentService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        bundleContentService = new BundleContentServiceImpl(bundleContentRepository, aiContentClient, objectMapper);
    }

    private ArticleBundleDTO sampleBundle() {
        ArticleBundleDTO.BundleItemDTO cafe = new ArticleBundleDTO.BundleItemDTO(
            "id-cafe", "Café", BigDecimal.valueOf(500), BigDecimal.valueOf(20), null
        );
        ArticleBundleDTO.BundleItemDTO croissant = new ArticleBundleDTO.BundleItemDTO(
            "id-croissant", "Croissant", BigDecimal.valueOf(300), BigDecimal.valueOf(30), null
        );
        ArticleBundleDTO.BundleDTO bundleDTO = new ArticleBundleDTO.BundleDTO(
            cafe, croissant, 3,
            BigDecimal.valueOf(50), BigDecimal.valueOf(100), BigDecimal.valueOf(2),
            BigDecimal.valueOf(800), BigDecimal.valueOf(500)
        );
        return new ArticleBundleDTO("matin", "Matin (6h-11h)", List.of(bundleDTO));
    }

    @Test
    void generateAndStore_persistsNewEntity_whenNoneExistsForKey() {
        ArticleBundleDTO bundle = sampleBundle();
        when(bundleContentRepository.findByKey("matin")).thenReturn(null);
        when(aiContentClient.generate(bundle)).thenReturn(
            new AiGeneratedContentDTO("Texte marketing", "Conseil d'usage")
        );

        BundleContentDTO result = bundleContentService.generateAndStore(bundle);

        assertEquals("matin", result.key());
        assertEquals("Matin (6h-11h)", result.label());
        assertEquals("Texte marketing", result.marketingText());
        assertEquals("Conseil d'usage", result.usageTip());
        assertEquals(1, result.bundle().bundles().size());
        verify(bundleContentRepository).persist(any(BundleContent.class));
    }

    @Test
    void generateAndStore_updatesExistingEntity_whenOneAlreadyExistsForKey() {
        ArticleBundleDTO bundle = sampleBundle();
        BundleContent existing = new BundleContent();
        existing.id = new ObjectId();
        existing.setKey("matin");
        when(bundleContentRepository.findByKey("matin")).thenReturn(existing);
        when(aiContentClient.generate(bundle)).thenReturn(
            new AiGeneratedContentDTO("Nouveau texte", "Nouveau conseil")
        );

        bundleContentService.generateAndStore(bundle);

        verify(bundleContentRepository).update(existing);
        assertEquals("Nouveau texte", existing.getMarketingText());
    }

    @Test
    void getStored_returnsDeserializedBundle_whenContentExists() {
        ArticleBundleDTO bundle = sampleBundle();
        BundleContent existing = new BundleContent();
        existing.id = new ObjectId();
        existing.setKey("matin");
        existing.setLabel("Matin (6h-11h)");
        existing.setMarketingText("Texte marketing");
        existing.setUsageTip("Conseil d'usage");
        try {
            existing.setBundleJson(objectMapper.writeValueAsString(bundle));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(bundleContentRepository.findByKey("matin")).thenReturn(existing);

        BundleContentDTO result = bundleContentService.getStored("matin");

        assertEquals("matin", result.key());
        assertEquals(1, result.bundle().bundles().size());
        assertEquals("Café", result.bundle().bundles().get(0).first().name());
    }

    @Test
    void getStored_throwsNotFound_whenNoContentExistsForKey() {
        when(bundleContentRepository.findByKey("soir")).thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> bundleContentService.getStored("soir")
        );

        assertEquals(404, exception.getErrorCode().getStatusCode());
    }
}
