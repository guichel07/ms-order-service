package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import org.acme.Entity.Alert;

public record AlertResponseDTO(
    String id,
    String orderId,
    String articleId,
    String vendor,
    String productName,
    BigDecimal attemptedPrice,
    BigDecimal costPrice,
    BigDecimal margin,
    BigDecimal quantity,
    Instant detectedAt,
    boolean resolved
) {
    public static AlertResponseDTO fromEntity(Alert alert) {
        return new AlertResponseDTO(
            alert.id.toHexString(),
            alert.getOrderId(),
            alert.getArticleId(),
            alert.getVendor(),
            alert.getProductName(),
            alert.getAttemptedPrice(),
            alert.getCostPrice(),
            alert.getMargin(),
            alert.getQuantity(),
            alert.getDetectedAt(),
            alert.isResolved()
        );
    }
}
