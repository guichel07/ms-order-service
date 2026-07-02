package org.acme.Service.Stats.impl;

import jakarta.ws.rs.core.Response;
import org.acme.DTO.StatsResponseDTO;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link StatsServiceImpl}.
 * OrderRepository est mocké : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private StatsServiceImpl statsService;

    private Order buildOrder(String email, Instant saleDate, BigDecimal delta, OrderItem... items) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setEmail(email);
        order.setSaleDate(saleDate);
        order.setDelta(delta);
        order.setArticles(new ArrayList<>(List.of(items)));
        return order;
    }

    // ---------------------------------------------------------------
    // période invalide
    // ---------------------------------------------------------------

    @Test
    void getStats_shouldThrowBadRequest_whenPeriodIsUnknown() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> statsService.getStats("foo")
        );

        assertEquals(Response.Status.BAD_REQUEST, exception.getErrorCode());
        verify(orderRepository, never()).findBySaleDateRange(any(), any());
    }

    // ---------------------------------------------------------------
    // fenêtres courante/précédente
    // ---------------------------------------------------------------

    private Instant[] captureWindows(String period) {
        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        when(orderRepository.findBySaleDateRange(startCaptor.capture(), endCaptor.capture()))
                .thenReturn(List.of(), List.of());

        statsService.getStats(period);

        List<Instant> starts = startCaptor.getAllValues();
        List<Instant> ends = endCaptor.getAllValues();

        // [currentStart, currentEnd, previousStart, previousEnd]
        return new Instant[] { starts.get(0), ends.get(0), starts.get(1), ends.get(1) };
    }

    private long daysBetween(Instant a, Instant b) {
        ZoneId zone = ZoneId.systemDefault();
        return ChronoUnit.DAYS.between(LocalDate.ofInstant(a, zone), LocalDate.ofInstant(b, zone));
    }

    private long monthsBetween(Instant a, Instant b) {
        ZoneId zone = ZoneId.systemDefault();
        return ChronoUnit.MONTHS.between(LocalDate.ofInstant(a, zone), LocalDate.ofInstant(b, zone));
    }

    @Test
    void getStats_shouldQueryContiguousWindows_for7j() {
        Instant[] w = captureWindows("7j");
        assertEquals(w[0], w[3]); // previousEnd == currentStart : pas de trou ni chevauchement
        assertEquals(7, daysBetween(w[0], w[1]));
        assertEquals(7, daysBetween(w[2], w[3]));
    }

    @Test
    void getStats_shouldQueryContiguousWindows_for4sem() {
        Instant[] w = captureWindows("4sem");
        assertEquals(w[0], w[3]);
        assertEquals(28, daysBetween(w[0], w[1]));
        assertEquals(28, daysBetween(w[2], w[3]));
    }

    @Test
    void getStats_shouldQueryContiguousWindows_for6mois() {
        Instant[] w = captureWindows("6mois");
        assertEquals(w[0], w[3]);
        assertEquals(6, monthsBetween(w[0], w[1]));
        assertEquals(6, monthsBetween(w[2], w[3]));
    }

    @Test
    void getStats_shouldQueryContiguousWindows_for2ans() {
        Instant[] w = captureWindows("2ans");
        assertEquals(w[0], w[3]);
        assertEquals(24, monthsBetween(w[0], w[1]));
        assertEquals(24, monthsBetween(w[2], w[3]));
    }

    // ---------------------------------------------------------------
    // calcul du CA / bénéfice
    // ---------------------------------------------------------------

    @Test
    void getStats_shouldAccumulateCaFromArticles_notFromAnyStoredField() {
        Order order = buildOrder(
                "seller@test.com",
                Instant.now(),
                BigDecimal.TEN,
                new OrderItem("a1", "Café", BigDecimal.valueOf(10), 2),
                new OrderItem("a2", "Thé", BigDecimal.valueOf(5), 3)
        );

        when(orderRepository.findBySaleDateRange(any(), any()))
                .thenReturn(List.of(order), List.of());

        StatsResponseDTO result = statsService.getStats("7j");

        assertEquals(new BigDecimal("35.00"), result.metrics().get("seller@test.com").ca());
    }

    @Test
    void getStats_shouldTreatNullDeltaAsZero_forLegacyOrders() {
        Order legacyOrder = buildOrder("seller@test.com", Instant.now(), null);
        Order recentOrder = buildOrder("seller@test.com", Instant.now(), BigDecimal.TEN);

        when(orderRepository.findBySaleDateRange(any(), any()))
                .thenReturn(List.of(legacyOrder, recentOrder), List.of());

        StatsResponseDTO result = statsService.getStats("7j");

        assertEquals(new BigDecimal("10.00"), result.metrics().get("seller@test.com").benefice());
    }

    // ---------------------------------------------------------------
    // delta vs période précédente
    // ---------------------------------------------------------------

    @Test
    void getStats_shouldComputeDeltaPercentage_whenPreviousPeriodHadSales() {
        Order current = buildOrder(
                "seller@test.com", Instant.now(), BigDecimal.ZERO,
                new OrderItem("a1", "Café", BigDecimal.valueOf(1000), 1)
        );
        Order previous = buildOrder(
                "seller@test.com", Instant.now(), BigDecimal.ZERO,
                new OrderItem("a1", "Café", BigDecimal.valueOf(800), 1)
        );

        when(orderRepository.findBySaleDateRange(any(), any()))
                .thenReturn(List.of(current), List.of(previous));

        StatsResponseDTO result = statsService.getStats("7j");

        assertEquals(new BigDecimal("25.0"), result.delta().pct());
        assertTrue(result.delta().positive());
        assertEquals("vs 7 jours précédents", result.delta().label());
    }

    @Test
    void getStats_shouldOmitDelta_whenPreviousPeriodHadNoSales() {
        Order current = buildOrder(
                "seller@test.com", Instant.now(), BigDecimal.ZERO,
                new OrderItem("a1", "Café", BigDecimal.valueOf(1000), 1)
        );

        when(orderRepository.findBySaleDateRange(any(), any()))
                .thenReturn(List.of(current), List.of());

        StatsResponseDTO result = statsService.getStats("7j");

        assertNull(result.delta());
    }

    // ---------------------------------------------------------------
    // trend / bySeller
    // ---------------------------------------------------------------

    @Test
    void getStats_shouldBuildTrendBucketsWithExactBySellerBreakdown() {
        Order sellerAOrder = buildOrder(
                "a@test.com", Instant.now(), BigDecimal.ZERO,
                new OrderItem("a1", "Café", BigDecimal.valueOf(10), 1)
        );
        Order sellerBOrder = buildOrder(
                "b@test.com", Instant.now().minus(2, ChronoUnit.DAYS), BigDecimal.ZERO,
                new OrderItem("a2", "Thé", BigDecimal.valueOf(5), 1)
        );

        when(orderRepository.findBySaleDateRange(any(), any()))
                .thenReturn(List.of(sellerAOrder, sellerBOrder), List.of());

        StatsResponseDTO result = statsService.getStats("7j");

        assertEquals(7, result.trend().size());

        // Bucket "aujourd'hui" (index 6, le dernier) : seul le vendeur A a vendu.
        StatsResponseDTO.TrendBucketDTO todayBucket = result.trend().get(6);
        assertEquals(new BigDecimal("10.00"), todayBucket.bySeller().get("a@test.com").ca());
        // Le vendeur B doit apparaître explicitement à zéro, jamais être absent
        // (sinon le front retombe sur son estimation au prorata).
        assertTrue(todayBucket.bySeller().containsKey("b@test.com"));
        assertEquals(new BigDecimal("0.00"), todayBucket.bySeller().get("b@test.com").ca());

        // Bucket "il y a 2 jours" (index 4) : seul le vendeur B a vendu.
        StatsResponseDTO.TrendBucketDTO twoDaysAgoBucket = result.trend().get(4);
        assertEquals(new BigDecimal("5.00"), twoDaysAgoBucket.bySeller().get("b@test.com").ca());
        assertTrue(twoDaysAgoBucket.bySeller().containsKey("a@test.com"));
        assertEquals(new BigDecimal("0.00"), twoDaysAgoBucket.bySeller().get("a@test.com").ca());
    }

    @Test
    void getStats_shouldScopeHourlyPatternAndFirstLastSaleTime_perBucketAndTeamWide() {
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();

        Order morningSale = buildOrder(
                "a@test.com", startOfToday.plus(9, ChronoUnit.HOURS), BigDecimal.ZERO,
                new OrderItem("a1", "Café", BigDecimal.valueOf(10), 1)
        );
        Order afternoonSale = buildOrder(
                "b@test.com", startOfToday.plus(15, ChronoUnit.HOURS), BigDecimal.ZERO,
                new OrderItem("a2", "Thé", BigDecimal.valueOf(5), 1)
        );
        // Vente 2 jours avant : ne doit pas polluer le hourlyPattern du bucket "aujourd'hui",
        // ni son firstSaleTime/lastSaleTime, seulement celui de son propre bucket.
        Order twoDaysAgoSale = buildOrder(
                "a@test.com", Instant.now().minus(2, ChronoUnit.DAYS), BigDecimal.ZERO,
                new OrderItem("a3", "Chocolat", BigDecimal.valueOf(7), 1)
        );

        when(orderRepository.findBySaleDateRange(any(), any()))
                .thenReturn(List.of(morningSale, afternoonSale, twoDaysAgoSale), List.of());

        StatsResponseDTO result = statsService.getStats("7j");

        // Bucket "aujourd'hui" (dernier bucket) : les deux ventes du jour, scopées à ce bucket.
        StatsResponseDTO.TrendBucketDTO todayBucket = result.trend().get(6);
        assertEquals("09:00", todayBucket.firstSaleTime());
        assertEquals("15:00", todayBucket.lastSaleTime());
        assertEquals(2, todayBucket.hourlyPattern().size());

        // Bucket "il y a 2 jours" : une seule vente, propre à ce bucket.
        StatsResponseDTO.TrendBucketDTO twoDaysAgoBucket = result.trend().get(4);
        assertEquals(1, twoDaysAgoBucket.hourlyPattern().size());
        assertEquals(twoDaysAgoBucket.firstSaleTime(), twoDaysAgoBucket.lastSaleTime());

        // Vue équipe globale : le first/last sale time est scopé au dernier jour actif
        // (aujourd'hui), toute équipe confondue — la vente d'il y a 2 jours ne doit pas y figurer.
        assertEquals("09:00", result.firstSaleTime());
        assertEquals("15:00", result.lastSaleTime());
        // Le hourlyPattern global, lui, agrège toute la période (les 3 ventes) — au moins
        // les heures 9 et 15 doivent y apparaître.
        assertTrue(result.hourlyPattern().stream().anyMatch(p -> p.hour() == 9));
        assertTrue(result.hourlyPattern().stream().anyMatch(p -> p.hour() == 15));
    }

    @Test
    void getStats_shouldSparsifyLabels_for2ansPeriod() {
        when(orderRepository.findBySaleDateRange(any(), any()))
                .thenReturn(List.of(), List.of());

        StatsResponseDTO result = statsService.getStats("2ans");

        assertEquals(24, result.trend().size());

        for (int i = 0; i < 24; i++) {
            StatsResponseDTO.TrendBucketDTO bucket = result.trend().get(i);
            assertFalse(bucket.key().isBlank());

            boolean shouldHaveLabel = i % 6 == 0 || i == 23;
            if (shouldHaveLabel) {
                assertFalse(bucket.label().isBlank());
            } else {
                assertEquals("", bucket.label());
            }
        }
    }
}
