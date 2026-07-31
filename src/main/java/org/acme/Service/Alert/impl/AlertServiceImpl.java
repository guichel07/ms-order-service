package org.acme.Service.Alert.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.acme.DTO.AlertResponseDTO;
import org.acme.Entity.Alert;
import org.acme.Exception.BusinessException;
import org.acme.Repository.AlertRepository;
import org.acme.Service.Alert.AlertService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;

    public AlertServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public List<AlertResponseDTO> listAll() {
        return alertRepository
            .listAllNewestFirst()
            .stream()
            .map(AlertResponseDTO::fromEntity)
            .toList();
    }

    @Override
    @Transactional
    public AlertResponseDTO toggleResolved(String id) {
        Alert alert = Optional.ofNullable(alertRepository.findById(new ObjectId(id)))
            .orElseThrow(() -> new BusinessException(Response.Status.NOT_FOUND, "Alerte introuvable " + id));

        alert.setResolved(!alert.isResolved());

        return AlertResponseDTO.fromEntity(alert);
    }

    @Override
    @Transactional
    public AlertResponseDTO recordNegativeMarginSale(
        String orderId,
        String articleId,
        String vendor,
        String productName,
        BigDecimal attemptedPrice,
        BigDecimal costPrice,
        BigDecimal margin,
        int quantity
    ) {
        Alert alert = new Alert();
        alert.setOrderId(orderId);
        alert.setArticleId(articleId);
        alert.setVendor(vendor);
        alert.setProductName(productName);
        alert.setAttemptedPrice(attemptedPrice);
        alert.setCostPrice(costPrice);
        alert.setMargin(margin);
        alert.setQuantity(quantity);
        alert.setDetectedAt(Instant.now());
        alert.setResolved(false);

        alertRepository.persist(alert);

        return AlertResponseDTO.fromEntity(alert);
    }
}
