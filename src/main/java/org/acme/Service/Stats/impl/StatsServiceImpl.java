package org.acme.Service.Stats.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.acme.DTO.StatsResponseDTO;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Stats.StatsService;

@ApplicationScoped
public class StatsServiceImpl implements StatsService {

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern(
        "dd/MM",
        Locale.FRENCH
    );
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern(
        "MMM yyyy",
        Locale.FRENCH
    );
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");

    private final OrderRepository orderRepository;

    public StatsServiceImpl(OrderRepository _orderRepository) {
        this.orderRepository = _orderRepository;
    }

    private record BucketDef(Instant start, Instant end, String key, String label) {}

    private record PeriodDefinition(
        Instant windowStart,
        Instant windowEnd,
        Instant previousStart,
        Instant previousEnd,
        List<BucketDef> buckets,
        String deltaLabel
    ) {}

    @Override
    public StatsResponseDTO getStats(String period) {
        ZoneId zone = ZoneId.systemDefault();
        PeriodDefinition def = resolvePeriod(period);

        List<Order> currentOrders = orderRepository.findBySaleDateRange(
            def.windowStart(),
            def.windowEnd()
        );
        List<Order> previousOrders = orderRepository.findBySaleDateRange(
            def.previousStart(),
            def.previousEnd()
        );

        int bucketCount = def.buckets().size();

        Map<String, BigDecimal> caByEmail = new HashMap<>();
        Map<String, BigDecimal> beneficeByEmail = new HashMap<>();
        List<BigDecimal> bucketCa = new ArrayList<>();
        List<BigDecimal> bucketBenefice = new ArrayList<>();
        List<Map<String, BigDecimal>> bucketCaBySeller = new ArrayList<>();
        List<Map<String, BigDecimal>> bucketBeneficeBySeller = new ArrayList<>();
        Map<Integer, int[]> hourlyCounts = new HashMap<>();
        Map<Integer, BigDecimal> hourlyCa = new HashMap<>();
        List<Map<Integer, int[]>> bucketHourlyCounts = new ArrayList<>();
        List<Map<Integer, BigDecimal>> bucketHourlyCa = new ArrayList<>();
        Instant[] bucketFirstSale = new Instant[bucketCount];
        Instant[] bucketLastSale = new Instant[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            bucketCa.add(BigDecimal.ZERO);
            bucketBenefice.add(BigDecimal.ZERO);
            bucketCaBySeller.add(new HashMap<>());
            bucketBeneficeBySeller.add(new HashMap<>());
            bucketHourlyCounts.add(new HashMap<>());
            bucketHourlyCa.add(new HashMap<>());
        }

        for (Order order : currentOrders) {
            String email = order.getEmail();
            if (email == null || email.isBlank()) {
                continue;
            }

            BigDecimal orderCa = computeOrderCa(order);
            BigDecimal orderBenefice = order.getDelta() != null
                ? order.getDelta()
                : BigDecimal.ZERO;

            caByEmail.merge(email, orderCa, BigDecimal::add);
            beneficeByEmail.merge(email, orderBenefice, BigDecimal::add);

            int hour = order.getSaleDate().atZone(zone).getHour();
            hourlyCounts.computeIfAbsent(hour, h -> new int[1])[0]++;
            hourlyCa.merge(hour, orderCa, BigDecimal::add);

            int bucketIndex = findBucketIndex(def.buckets(), order.getSaleDate());
            if (bucketIndex < 0) {
                continue;
            }

            bucketCa.set(bucketIndex, bucketCa.get(bucketIndex).add(orderCa));
            bucketBenefice.set(
                bucketIndex,
                bucketBenefice.get(bucketIndex).add(orderBenefice)
            );
            bucketCaBySeller.get(bucketIndex).merge(email, orderCa, BigDecimal::add);
            bucketBeneficeBySeller.get(bucketIndex).merge(email, orderBenefice, BigDecimal::add);
            bucketHourlyCounts.get(bucketIndex).computeIfAbsent(hour, h -> new int[1])[0]++;
            bucketHourlyCa.get(bucketIndex).merge(hour, orderCa, BigDecimal::add);

            Instant saleDate = order.getSaleDate();
            if (bucketFirstSale[bucketIndex] == null || saleDate.isBefore(bucketFirstSale[bucketIndex])) {
                bucketFirstSale[bucketIndex] = saleDate;
            }
            if (bucketLastSale[bucketIndex] == null || saleDate.isAfter(bucketLastSale[bucketIndex])) {
                bucketLastSale[bucketIndex] = saleDate;
            }
        }

        Map<String, StatsResponseDTO.MetricsDTO> metrics = new HashMap<>();
        for (String email : caByEmail.keySet()) {
            metrics.put(
                email,
                new StatsResponseDTO.MetricsDTO(
                    round(caByEmail.get(email)),
                    round(beneficeByEmail.getOrDefault(email, BigDecimal.ZERO))
                )
            );
        }

        List<StatsResponseDTO.TrendBucketDTO> trend = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            final int bucketIdx = i;
            BucketDef bucketDef = def.buckets().get(i);
            Map<String, StatsResponseDTO.MetricsDTO> bySeller = new HashMap<>();
            for (String email : caByEmail.keySet()) {
                bySeller.put(
                    email,
                    new StatsResponseDTO.MetricsDTO(
                        round(bucketCaBySeller.get(i).getOrDefault(email, BigDecimal.ZERO)),
                        round(
                            bucketBeneficeBySeller.get(i).getOrDefault(email, BigDecimal.ZERO)
                        )
                    )
                );
            }

            List<StatsResponseDTO.HourlyPointDTO> bucketHourly = bucketHourlyCounts.get(i).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new StatsResponseDTO.HourlyPointDTO(
                    e.getKey(),
                    e.getValue()[0],
                    round(bucketHourlyCa.get(bucketIdx).getOrDefault(e.getKey(), BigDecimal.ZERO))
                ))
                .toList();

            trend.add(
                new StatsResponseDTO.TrendBucketDTO(
                    bucketDef.key(),
                    bucketDef.label(),
                    round(bucketCa.get(i)),
                    round(bucketBenefice.get(i)),
                    bySeller,
                    bucketFirstSale[i] == null ? null : bucketFirstSale[i].atZone(zone).format(HOUR_LABEL),
                    bucketLastSale[i] == null ? null : bucketLastSale[i].atZone(zone).format(HOUR_LABEL),
                    bucketHourly
                )
            );
        }

        BigDecimal previousTotalCa = BigDecimal.ZERO;
        for (Order order : previousOrders) {
            previousTotalCa = previousTotalCa.add(computeOrderCa(order));
        }

        BigDecimal currentTotalCa = BigDecimal.ZERO;
        for (BigDecimal ca : caByEmail.values()) {
            currentTotalCa = currentTotalCa.add(ca);
        }

        StatsResponseDTO.PeriodDeltaDTO delta = null;
        if (previousTotalCa.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = currentTotalCa
                .subtract(previousTotalCa)
                .divide(previousTotalCa, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

            delta = new StatsResponseDTO.PeriodDeltaDTO(
                pct,
                currentTotalCa.compareTo(previousTotalCa) >= 0,
                def.deltaLabel()
            );
        }

        List<StatsResponseDTO.HourlyPointDTO> hourlyPattern = hourlyCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new StatsResponseDTO.HourlyPointDTO(
                e.getKey(),
                e.getValue()[0],
                round(hourlyCa.getOrDefault(e.getKey(), BigDecimal.ZERO))
            ))
            .toList();

        String firstSaleTime = null;
        String lastSaleTime = null;
        Order lastOrderOverall = currentOrders.stream()
            .filter(o -> o.getSaleDate() != null)
            .max(Comparator.comparing(Order::getSaleDate))
            .orElse(null);
        if (lastOrderOverall != null) {
            LocalDate lastActiveDay = lastOrderOverall.getSaleDate().atZone(zone).toLocalDate();
            List<Order> ordersOnThatDay = currentOrders.stream()
                .filter(o -> o.getSaleDate() != null
                    && o.getSaleDate().atZone(zone).toLocalDate().equals(lastActiveDay))
                .sorted(Comparator.comparing(Order::getSaleDate))
                .toList();
            firstSaleTime = ordersOnThatDay.get(0).getSaleDate().atZone(zone).format(HOUR_LABEL);
            lastSaleTime = ordersOnThatDay.get(ordersOnThatDay.size() - 1).getSaleDate().atZone(zone).format(HOUR_LABEL);
        }

        return new StatsResponseDTO(metrics, delta, trend, firstSaleTime, lastSaleTime, hourlyPattern);
    }

    private PeriodDefinition resolvePeriod(String period) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        return switch (period) {
            case "7j" -> dailyPeriod(today, zone, 7, "vs 7 jours précédents");
            case "4sem" -> weeklyPeriod(today, zone, 4, "vs 4 semaines précédentes");
            case "6mois" -> monthlyPeriod(today, zone, 6, "vs 6 mois précédents", false);
            case "2ans" -> monthlyPeriod(today, zone, 24, "vs 2 ans précédents", true);
            default -> throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "Période inconnue : " + period + ". Valeurs autorisées : 7j, 4sem, 6mois, 2ans"
            );
        };
    }

    private PeriodDefinition dailyPeriod(
        LocalDate today,
        ZoneId zone,
        int count,
        String deltaLabel
    ) {
        LocalDate firstDay = today.minusDays(count - 1L);
        List<BucketDef> buckets = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDate day = firstDay.plusDays(i);
            Instant start = day.atStartOfDay(zone).toInstant();
            Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
            buckets.add(new BucketDef(start, end, day.format(DAY_KEY), day.format(DAY_LABEL)));
        }

        Instant windowStart = buckets.get(0).start();
        Instant windowEnd = buckets.get(buckets.size() - 1).end();
        Instant previousStart = firstDay.minusDays(count).atStartOfDay(zone).toInstant();

        return new PeriodDefinition(
            windowStart,
            windowEnd,
            previousStart,
            windowStart,
            buckets,
            deltaLabel
        );
    }

    private PeriodDefinition weeklyPeriod(
        LocalDate today,
        ZoneId zone,
        int count,
        String deltaLabel
    ) {
        LocalDate mondayThisWeek = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate firstMonday = mondayThisWeek.minusWeeks(count - 1L);
        List<BucketDef> buckets = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDate weekStart = firstMonday.plusWeeks(i);
            Instant start = weekStart.atStartOfDay(zone).toInstant();
            Instant end = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant();
            buckets.add(
                new BucketDef(start, end, weekStart.format(DAY_KEY), weekStart.format(DAY_LABEL))
            );
        }

        Instant windowStart = buckets.get(0).start();
        Instant windowEnd = buckets.get(buckets.size() - 1).end();
        Instant previousStart = firstMonday.minusWeeks(count).atStartOfDay(zone).toInstant();

        return new PeriodDefinition(
            windowStart,
            windowEnd,
            previousStart,
            windowStart,
            buckets,
            deltaLabel
        );
    }

    private PeriodDefinition monthlyPeriod(
        LocalDate today,
        ZoneId zone,
        int count,
        String deltaLabel,
        boolean sparsifyLabels
    ) {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = currentMonth.minusMonths(count - 1L);
        List<BucketDef> buckets = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
            Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
            boolean showLabel = !sparsifyLabels || i % 6 == 0 || i == count - 1;
            String label = showLabel ? month.atDay(1).format(MONTH_LABEL) : "";
            buckets.add(new BucketDef(start, end, month.format(MONTH_KEY), label));
        }

        Instant windowStart = buckets.get(0).start();
        Instant windowEnd = buckets.get(buckets.size() - 1).end();
        Instant previousStart = firstMonth
            .minusMonths(count)
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant();

        return new PeriodDefinition(
            windowStart,
            windowEnd,
            previousStart,
            windowStart,
            buckets,
            deltaLabel
        );
    }

    private BigDecimal computeOrderCa(Order order) {
        List<OrderItem> items = order.getArticles();
        if (items == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getPrice().multiply(item.getQuantityOrdered()));
        }
        return total;
    }

    private int findBucketIndex(List<BucketDef> buckets, Instant saleDate) {
        if (saleDate == null) {
            return -1;
        }

        for (int i = 0; i < buckets.size(); i++) {
            BucketDef bucket = buckets.get(i);
            if (!saleDate.isBefore(bucket.start()) && saleDate.isBefore(bucket.end())) {
                return i;
            }
        }
        return -1;
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
