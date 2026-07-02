package org.acme.Service.Article.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.acme.DTO.ArticleRankingEntryDTO;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link ArticleRankingServiceImpl}.
 * OrderRepository est mocké : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class ArticleRankingServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ArticleRankingServiceImpl articleRankingService;

    private Order buildOrder(Instant saleDate, OrderItem... items) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setSaleDate(saleDate);
        order.setArticles(new ArrayList<>(List.of(items)));
        return order;
    }

    private OrderItem item(String articleId, String name, long price, int qty, long unitCost) {
        OrderItem item = new OrderItem(articleId, name, BigDecimal.valueOf(price), BigDecimal.valueOf(qty));
        item.setUnitCost(BigDecimal.valueOf(unitCost));
        return item;
    }

    @Test
    void getRanking_sortsArticlesByCaDescending() {
        String popular = "art-1";
        String niche = "art-2";

        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(List.of(
            buildOrder(Instant.now().minus(1, ChronoUnit.DAYS),
                item(popular, "Sac de riz 25kg", 12000, 3, 8000)),
            buildOrder(Instant.now().minus(2, ChronoUnit.DAYS),
                item(niche, "Parfum bio", 15000, 1, 9000))
        ));

        List<ArticleRankingEntryDTO> ranking = articleRankingService.getRanking("7j");

        assertEquals(2, ranking.size());
        assertEquals(popular, ranking.get(0).articleId());
        assertEquals(0, new BigDecimal("36000.00").compareTo(ranking.get(0).totalCa()));
        assertEquals(0, new BigDecimal("12000.00").compareTo(ranking.get(0).totalBenefice()));
        assertEquals(niche, ranking.get(1).articleId());
    }

    @Test
    void getRanking_aggregatesQuantityAcrossMultipleOrders_forTheSameArticle() {
        String articleId = "art-1";
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(List.of(
            buildOrder(Instant.now().minus(1, ChronoUnit.DAYS), item(articleId, "Savon noir", 500, 3, 300)),
            buildOrder(Instant.now().minus(2, ChronoUnit.DAYS), item(articleId, "Savon noir", 500, 2, 300))
        ));

        List<ArticleRankingEntryDTO> ranking = articleRankingService.getRanking("7j");

        assertEquals(1, ranking.size());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(ranking.get(0).quantitySold()));
    }

    @Test
    void getRanking_throwsBadRequest_whenPeriodIsUnknown() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> articleRankingService.getRanking("foo")
        );
        assertEquals(jakarta.ws.rs.core.Response.Status.BAD_REQUEST, exception.getErrorCode());
    }
}
