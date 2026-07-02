package org.acme.Service.Article.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.acme.DTO.ArticleDetailDTO;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Repository.OrderRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link ArticleDetailServiceImpl}.
 * OrderRepository est mocké : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class ArticleDetailServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ArticleDetailServiceImpl articleDetailService;

    private Order buildOrder(Instant saleDate, OrderItem... items) {
        return buildOrder(saleDate, null, null, items);
    }

    private Order buildOrder(Instant saleDate, String email, String sellerName, OrderItem... items) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setSaleDate(saleDate);
        order.setEmail(email);
        order.setSellerName(sellerName);
        order.setArticles(new ArrayList<>(List.of(items)));
        return order;
    }

    private OrderItem item(String articleId, String name, long price, int qty, Long unitCost) {
        OrderItem item = new OrderItem(articleId, name, BigDecimal.valueOf(price), BigDecimal.valueOf(qty));
        if (unitCost != null) {
            item.setUnitCost(BigDecimal.valueOf(unitCost));
        }
        return item;
    }

    @Test
    void getDetail_returnsEmptyPeriods_whenArticleNeverSold() {
        String articleId = new ObjectId().toHexString();
        when(orderRepository.findByArticleId(articleId)).thenReturn(List.of());

        ArticleDetailDTO detail = articleDetailService.getDetail(articleId);

        assertEquals(4, detail.periods().size());
        assertNull(detail.alert());
        for (ArticleDetailDTO.ArticlePeriodDTO period : detail.periods()) {
            assertEquals(0, BigDecimal.ZERO.compareTo(period.quantitySold()));
            assertEquals(0, new BigDecimal("0.00").compareTo(period.velocityPerDay()));
        }
    }

    @Test
    void getDetail_onlySumsLinesMatchingThisArticle_ignoringOtherArticlesInSameOrder() {
        String articleId = new ObjectId().toHexString();
        String otherArticleId = new ObjectId().toHexString();

        Order mixedOrder = buildOrder(
            Instant.now().minus(1, ChronoUnit.DAYS),
            item(articleId, "Crème hydratante", 2500, 2, 1730L),
            item(otherArticleId, "Savon noir", 1200, 5, 865L)
        );
        when(orderRepository.findByArticleId(articleId)).thenReturn(List.of(mixedOrder));

        ArticleDetailDTO detail = articleDetailService.getDetail(articleId);
        ArticleDetailDTO.ArticlePeriodDTO period7j = periodByKey(detail, "7j");

        assertEquals(0, BigDecimal.valueOf(2).compareTo(period7j.quantitySold()));
        assertEquals(1, period7j.transactionsCount());
        assertEquals(0, new BigDecimal("5000.00").compareTo(period7j.totalCa()));
        assertEquals(0, new BigDecimal("1540.00").compareTo(period7j.totalBenefice()));
    }

    @Test
    void getDetail_treatsMissingUnitCost_asZeroBenefice_notAnError() {
        String articleId = new ObjectId().toHexString();
        Order legacyOrder = buildOrder(
            Instant.now().minus(1, ChronoUnit.DAYS),
            item(articleId, "Article ancien", 1000, 1, null)
        );
        when(orderRepository.findByArticleId(articleId)).thenReturn(List.of(legacyOrder));

        ArticleDetailDTO detail = articleDetailService.getDetail(articleId);
        ArticleDetailDTO.ArticlePeriodDTO period7j = periodByKey(detail, "7j");

        assertEquals(0, new BigDecimal("1000.00").compareTo(period7j.totalCa()));
        assertEquals(0, new BigDecimal("0.00").compareTo(period7j.totalBenefice()));
    }

    @Test
    void getDetail_raisesWarningAlert_whenVelocityDropsMoreThanThreshold() {
        String articleId = new ObjectId().toHexString();
        List<Order> orders = new ArrayList<>();
        for (int i = 8; i <= 12; i++) {
            orders.add(buildOrder(Instant.now().minus(i, ChronoUnit.DAYS), item(articleId, "Vitamines C 1000", 3000, 5, 1500L)));
        }
        orders.add(buildOrder(Instant.now().minus(1, ChronoUnit.DAYS), item(articleId, "Vitamines C 1000", 3000, 1, 1500L)));
        when(orderRepository.findByArticleId(articleId)).thenReturn(orders);

        ArticleDetailDTO detail = articleDetailService.getDetail(articleId);

        assertEquals("warning", detail.alert().level());
        assertTrue(detail.alert().message().contains("baisse"));
    }

    @Test
    void getDetail_computesVelocityPerDay_overTheNominalPeriodLength() {
        String articleId = new ObjectId().toHexString();
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Order order = buildOrder(startOfToday.plus(9, ChronoUnit.HOURS), item(articleId, "Huile essentielle", 1800, 14, 900L));
        when(orderRepository.findByArticleId(articleId)).thenReturn(List.of(order));

        ArticleDetailDTO detail = articleDetailService.getDetail(articleId);
        ArticleDetailDTO.ArticlePeriodDTO period7j = periodByKey(detail, "7j");

        // 14 unités vendues sur une fenêtre de 7 jours -> 2/jour.
        assertEquals(0, new BigDecimal("2.00").compareTo(period7j.velocityPerDay()));
    }

    @Test
    void getDetail_computesMarginRatePct_fromBeneficeOverCa() {
        String articleId = new ObjectId().toHexString();
        // CA 5000 (2500x2), coût 1730x2=3460 -> bénéfice 1540 -> 1540/5000 = 30.8%.
        Order order = buildOrder(Instant.now().minus(1, ChronoUnit.DAYS), item(articleId, "Crème hydratante", 2500, 2, 1730L));
        when(orderRepository.findByArticleId(articleId)).thenReturn(List.of(order));

        ArticleDetailDTO detail = articleDetailService.getDetail(articleId);
        ArticleDetailDTO.ArticlePeriodDTO period7j = periodByKey(detail, "7j");

        assertEquals(0, new BigDecimal("30.80").compareTo(period7j.marginRatePct()));
    }

    @Test
    void getDetail_breaksDownSalesBySeller_sortedByCaDescending() {
        String articleId = new ObjectId().toHexString();
        Order bigSale = buildOrder(
            Instant.now().minus(1, ChronoUnit.DAYS), "moussa@test.com", "Moussa Ba",
            item(articleId, "Savon noir", 1200, 10, 865L)
        );
        Order smallSale = buildOrder(
            Instant.now().minus(2, ChronoUnit.DAYS), "awa@test.com", "Awa Diop",
            item(articleId, "Savon noir", 1200, 1, 865L)
        );
        when(orderRepository.findByArticleId(articleId)).thenReturn(List.of(smallSale, bigSale));

        ArticleDetailDTO detail = articleDetailService.getDetail(articleId);
        ArticleDetailDTO.ArticlePeriodDTO period7j = periodByKey(detail, "7j");

        assertEquals(2, period7j.bySeller().size());
        assertEquals("Moussa Ba", period7j.bySeller().get(0).sellerName());
        assertEquals(0, BigDecimal.valueOf(10).compareTo(period7j.bySeller().get(0).quantitySold()));
        assertEquals("Awa Diop", period7j.bySeller().get(1).sellerName());
        assertEquals(0, BigDecimal.valueOf(1).compareTo(period7j.bySeller().get(1).quantitySold()));
    }

    @Test
    void getDetail_surfacesAssociatedProduct_onlyWhenLiftShowsARealLink_notJustPopularity() {
        String articleA = new ObjectId().toHexString();
        String articleB = new ObjectId().toHexString();
        String articleC = new ObjectId().toHexString();

        List<Order> aOrders = new ArrayList<>();
        List<Order> allOrders = new ArrayList<>();
        // 3 commandes A+B+C : B et C sont co-achetés avec A au même taux.
        for (int i = 0; i < 3; i++) {
            Order o = buildOrder(
                Instant.now().minus(i, ChronoUnit.DAYS),
                item(articleA, "A", 1000, 1, 500L),
                item(articleB, "B", 500, 1, 200L),
                item(articleC, "C", 300, 1, 100L)
            );
            aOrders.add(o);
            allOrders.add(o);
        }
        // 3 commandes C seul : C est populaire indépendamment de A, B ne l'est pas.
        for (int i = 3; i < 6; i++) {
            allOrders.add(buildOrder(Instant.now().minus(i, ChronoUnit.DAYS), item(articleC, "C", 300, 1, 100L)));
        }

        when(orderRepository.findByArticleId(articleA)).thenReturn(aOrders);
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(allOrders);

        ArticleDetailDTO detail = articleDetailService.getDetail(articleA);
        ArticleDetailDTO.ArticlePeriodDTO period7j = periodByKey(detail, "7j");

        // B : support 3/6=50%, confidence 3/3=100%, support(B)=3/6=50% -> lift=2.0 (vrai lien).
        // C : support 3/6=50%, confidence 3/3=100%, support(C)=6/6=100% -> lift=1.0 (juste populaire, filtré).
        assertEquals(1, period7j.topAssociatedProducts().size());
        ArticleDetailDTO.AssociatedProductDTO associated = period7j.topAssociatedProducts().get(0);
        assertEquals(articleB, associated.articleId());
        assertEquals(3, associated.coOccurrenceCount());
        assertEquals(0, new BigDecimal("2.0000").compareTo(associated.lift()));
    }

    private ArticleDetailDTO.ArticlePeriodDTO periodByKey(ArticleDetailDTO detail, String key) {
        return detail.periods().stream()
            .filter(p -> p.key().equals(key))
            .findFirst()
            .orElseThrow();
    }
}
