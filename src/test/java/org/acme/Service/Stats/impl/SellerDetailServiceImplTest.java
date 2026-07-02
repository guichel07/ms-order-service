package org.acme.Service.Stats.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.acme.DTO.SellerDetailDTO;
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
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link SellerDetailServiceImpl}.
 * OrderRepository est mocké : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class SellerDetailServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SellerDetailServiceImpl sellerDetailService;

    private Order buildOrder(String email, Instant saleDate, BigDecimal delta, OrderItem... items) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setEmail(email);
        order.setSaleDate(saleDate);
        order.setDelta(delta);
        order.setArticles(new ArrayList<>(List.of(items)));
        return order;
    }

    private OrderItem item(String name, long price, int qty) {
        return new OrderItem(null, name, BigDecimal.valueOf(price), BigDecimal.valueOf(qty));
    }

    @Test
    void getDetail_returnsEmptyPeriods_whenSellerHasNoOrders() {
        when(orderRepository.findByEmail("jean.dupont@example.com")).thenReturn(List.of());

        SellerDetailDTO detail = sellerDetailService.getDetail("jean.dupont@example.com");

        assertEquals(4, detail.periods().size());
        assertNull(detail.alert());
        assertNull(detail.firstSaleTime());
        assertNull(detail.lastSaleTime());
        for (SellerDetailDTO.SellerPeriodDTO period : detail.periods()) {
            assertEquals(0, period.transactionsCount());
        }
    }

    @Test
    void getDetail_computesTotalsAndFirstLastSaleTime_scopedToLastActiveDay() {
        String email = "awa.traore@example.com";
        ZoneId zone = ZoneId.systemDefault();
        // Ancré à midi aujourd'hui plutôt que "il y a 1h" — évite la fragilité autour de minuit.
        Instant noonToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().plus(12, ChronoUnit.HOURS);

        Order morningSale = buildOrder(
            email,
            noonToday,
            BigDecimal.valueOf(500),
            item("Crème hydratante", 2500, 2)
        );
        Order olderDaySale = buildOrder(
            email,
            Instant.now().minus(5, ChronoUnit.DAYS),
            BigDecimal.valueOf(300),
            item("Savon noir", 1200, 1)
        );
        when(orderRepository.findByEmail(email)).thenReturn(List.of(morningSale, olderDaySale));

        SellerDetailDTO detail = sellerDetailService.getDetail(email);

        assertEquals("Aujourd'hui", detail.lastActiveDayLabel());
        assertEquals(detail.firstSaleTime(), detail.lastSaleTime());

        SellerDetailDTO.SellerPeriodDTO period7j = periodByKey(detail, "7j");
        assertEquals(2, period7j.transactionsCount());
        assertEquals(0, new BigDecimal("6200.00").compareTo(period7j.totalCa()));
        assertEquals(0, BigDecimal.valueOf(3).compareTo(period7j.itemsSoldCount()));
    }

    @Test
    void getDetail_raisesCriticalAlert_whenNoSaleForTwoOrMoreDays() {
        String email = "moussa.diallo@example.com";
        Order order = buildOrder(
            email,
            Instant.now().minus(3, ChronoUnit.DAYS),
            BigDecimal.valueOf(500),
            item("Baume à lèvres", 700, 1)
        );
        when(orderRepository.findByEmail(email)).thenReturn(List.of(order));

        SellerDetailDTO detail = sellerDetailService.getDetail(email);

        assertEquals("critical", detail.alert().level());
    }

    @Test
    void getDetail_raisesWarningAlert_whenCadenceDropsMoreThanThreshold() {
        String email = "fatou.sarr@example.com";

        // Semaine précédente : forte activité.
        List<Order> orders = new ArrayList<>();
        for (int i = 8; i <= 12; i++) {
            orders.add(buildOrder(
                email,
                Instant.now().minus(i, ChronoUnit.DAYS),
                BigDecimal.valueOf(500),
                item("Vitamines C 1000", 3000, 1)
            ));
        }
        // Semaine courante : une seule petite vente -> grosse baisse de cadence.
        orders.add(buildOrder(
            email,
            Instant.now().minus(1, ChronoUnit.DAYS),
            BigDecimal.valueOf(100),
            item("Vitamines C 1000", 3000, 1)
        ));
        when(orderRepository.findByEmail(email)).thenReturn(orders);

        SellerDetailDTO detail = sellerDetailService.getDetail(email);

        assertEquals("warning", detail.alert().level());
        assertTrue(detail.alert().message().contains("baisse"));
    }

    @Test
    void getDetail_ranksTopItems_byRevenueDescending() {
        String email = "paul.mbemba@example.com";
        Order order = buildOrder(
            email,
            Instant.now().minus(1, ChronoUnit.DAYS),
            BigDecimal.valueOf(1000),
            item("Parfum bio", 12000, 1),
            item("Savon noir", 500, 3)
        );
        when(orderRepository.findByEmail(email)).thenReturn(List.of(order));

        SellerDetailDTO detail = sellerDetailService.getDetail(email);

        SellerDetailDTO.SellerPeriodDTO period7j = periodByKey(detail, "7j");
        assertEquals("Parfum bio", period7j.topItems().get(0).name());
        assertEquals(0, BigDecimal.valueOf(1).compareTo(period7j.topItems().get(0).quantity()));
        assertEquals("Savon noir", period7j.topItems().get(1).name());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(period7j.topItems().get(1).quantity()));
    }

    @Test
    void getDetail_scopesActivityBucketHourlyPatternToItsOwnDay_notTheWholePeriod() {
        String email = "grace.loemba@example.com";
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();

        // Deux ventes le même jour, à des heures différentes, dans le bucket "aujourd'hui".
        Order morningSale = buildOrder(
            email,
            startOfToday.plus(9, ChronoUnit.HOURS),
            BigDecimal.valueOf(500),
            item("Crème hydratante", 2500, 1)
        );
        Order afternoonSale = buildOrder(
            email,
            startOfToday.plus(15, ChronoUnit.HOURS),
            BigDecimal.valueOf(700),
            item("Vitamines C 1000", 3000, 1)
        );
        // Une vente 3 jours avant, dans un autre bucket "7j" : ne doit pas polluer le
        // hourlyPattern du bucket d'aujourd'hui, seulement celui de son propre bucket.
        Order threeDaysAgoSale = buildOrder(
            email,
            Instant.now().minus(3, ChronoUnit.DAYS),
            BigDecimal.valueOf(200),
            item("Savon noir", 1200, 1)
        );
        when(orderRepository.findByEmail(email))
            .thenReturn(List.of(morningSale, afternoonSale, threeDaysAgoSale));

        SellerDetailDTO detail = sellerDetailService.getDetail(email);
        SellerDetailDTO.SellerPeriodDTO period7j = periodByKey(detail, "7j");

        SellerDetailDTO.ActivityPointDTO todayBucket = period7j.activity().get(period7j.activity().size() - 1);
        assertEquals(2, todayBucket.salesCount());
        assertEquals(2, todayBucket.hourlyPattern().size());
        assertEquals("09:00", todayBucket.firstSaleTime());
        assertEquals("15:00", todayBucket.lastSaleTime());

        SellerDetailDTO.ActivityPointDTO threeDaysAgoBucket = period7j.activity().get(period7j.activity().size() - 4);
        assertEquals(1, threeDaysAgoBucket.salesCount());
        assertEquals(1, threeDaysAgoBucket.hourlyPattern().size());
    }

    private SellerDetailDTO.SellerPeriodDTO periodByKey(SellerDetailDTO detail, String key) {
        return detail.periods().stream()
            .filter(p -> p.key().equals(key))
            .findFirst()
            .orElseThrow();
    }
}
