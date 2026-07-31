package org.acme.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;

/**
 * Représentation d'une commande renvoyée par l'API — jamais l'entité Mongo directement.
 */
public record OrderResponseDTO(
    String id,
    String receiptNumber,
    Date receiptDate,
    String sellerName,
    String email,
    String clientId,
    String clientPhone,
    BigDecimal dailySummary,
    Instant saleDate,
    List<OrderItemResponseDTO> items,
    /** Bénéfice de la commande — calculé côté back, jamais reçu du client. */
    BigDecimal delta
) {
    public record OrderItemResponseDTO(
        String articleId,
        String name,
        BigDecimal price,
        int quantityOrdered
    ) {
        public static OrderItemResponseDTO fromEntity(OrderItem item) {
            if (item == null) {
                return null;
            }
            return new OrderItemResponseDTO(
                item.getArticleId(),
                item.getName(),
                item.getPrice(),
                item.getQuantityOrdered()
            );
        }
    }

    public static OrderResponseDTO fromEntity(Order order) {
        if (order == null) {
            return null;
        }
        List<OrderItemResponseDTO> items = order.getArticles() == null
            ? List.of()
            : order
                .getArticles()
                .stream()
                .map(OrderItemResponseDTO::fromEntity)
                .toList();

        return new OrderResponseDTO(
            order.id.toHexString(),
            order.getReceiptNumber(),
            order.getReceiptDate(),
            order.getSellerName(),
            order.getEmail(),
            order.getClientId(),
            order.getClientPhone(),
            order.getDailySummary(),
            order.getSaleDate(),
            items,
            order.getDelta()
        );
    }
}
