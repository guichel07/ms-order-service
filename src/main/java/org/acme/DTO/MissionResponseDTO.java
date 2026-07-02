package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;

public record MissionResponseDTO(
    String id,
    String questeId,
    String articleId,
    String name,
    String icon,
    String iconColor,
    String supplierId,
    String supplierName,
    int qty,
    BigDecimal pricePaid,
    BigDecimal refUnitCost,
    String status,
    Instant createdAt,
    BigDecimal marketPrice
) {}
