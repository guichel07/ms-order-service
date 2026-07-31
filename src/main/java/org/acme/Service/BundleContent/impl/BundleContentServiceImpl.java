package org.acme.Service.BundleContent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.BundleContentDTO;
import org.acme.Entity.BundleContent;
import org.acme.Exception.BusinessException;
import org.acme.Repository.BundleContentRepository;
import org.acme.Service.Ai.AiContentClient;
import org.acme.Service.BundleContent.BundleContentService;

@ApplicationScoped
public class BundleContentServiceImpl implements BundleContentService {

    private final BundleContentRepository bundleContentRepository;
    private final AiContentClient aiContentClient;
    private final ObjectMapper objectMapper;

    @Inject
    public BundleContentServiceImpl(
        BundleContentRepository _bundleContentRepository,
        AiContentClient _aiContentClient,
        ObjectMapper _objectMapper
    ) {
        this.bundleContentRepository = _bundleContentRepository;
        this.aiContentClient = _aiContentClient;
        this.objectMapper = _objectMapper;
    }

    @Override
    public BundleContentDTO generateAndStore(ArticleBundleDTO bundle) {
        AiGeneratedContentDTO generated = aiContentClient.generate(bundle);
        Instant generatedAt = Instant.now();

        BundleContent entity = bundleContentRepository.findByKey(bundle.periodKey());
        if (entity == null) {
            entity = new BundleContent();
            entity.setKey(bundle.periodKey());
        }
        entity.setLabel(bundle.label());
        entity.setBundleJson(writeBundleJson(bundle));
        entity.setMarketingText(generated.marketingText());
        entity.setUsageTip(generated.usageTip());
        entity.setGeneratedAt(generatedAt);

        if (entity.id == null) {
            bundleContentRepository.persist(entity);
        } else {
            bundleContentRepository.update(entity);
        }

        return new BundleContentDTO(
            bundle.periodKey(),
            bundle.label(),
            bundle,
            generated.marketingText(),
            generated.usageTip(),
            generatedAt
        );
    }

    @Override
    public BundleContentDTO getStored(String key) {
        BundleContent entity = bundleContentRepository.findByKey(key);
        if (entity == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Aucun contenu généré pour la clé : " + key
            );
        }

        return new BundleContentDTO(
            entity.getKey(),
            entity.getLabel(),
            readBundleJson(entity.getBundleJson()),
            entity.getMarketingText(),
            entity.getUsageTip(),
            entity.getGeneratedAt()
        );
    }

    private String writeBundleJson(ArticleBundleDTO bundle) {
        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (Exception e) {
            throw new BusinessException(
                Response.Status.INTERNAL_SERVER_ERROR,
                "Impossible de sérialiser le bundle : " + e.getMessage()
            );
        }
    }

    private ArticleBundleDTO readBundleJson(String json) {
        try {
            return objectMapper.readValue(json, ArticleBundleDTO.class);
        } catch (Exception e) {
            throw new BusinessException(
                Response.Status.INTERNAL_SERVER_ERROR,
                "Impossible de désérialiser le bundle stocké : " + e.getMessage()
            );
        }
    }
}
