package org.acme.Service.Stats.impl;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.acme.DTO.SellerDetailDTO;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Stats.SellerDetailService;
import org.acme.Util.OrderMath;
import jakarta.ws.rs.core.Response;

/**
 * Construit l'agrégat comportement + performance d'un vendeur (les 4 périodes en une
 * seule réponse — même choix que ClientDetailServiceImpl : tout d'un coup plutôt que
 * du lazy-loading, les requêtes restent bornées par vendeur).
 */
@ApplicationScoped
public class SellerDetailServiceImpl implements SellerDetailService {

    private static final int TOP_ITEMS_LIMIT = 3;
    /** Pas de vente depuis N jours ou plus -> alerte critique (vendeur inactif). */
    private static final int INACTIVE_DAYS_CRITICAL = 2;
    /** Baisse de CA (7 derniers jours vs 7 jours précédents) au-delà de ce seuil -> alerte warning. */
    private static final BigDecimal CADENCE_DROP_WARNING_PCT = BigDecimal.valueOf(20);

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH);
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");

    private final OrderRepository orderRepository;

    public SellerDetailServiceImpl(OrderRepository _orderRepository) {
        this.orderRepository = _orderRepository;
    }

    private record BucketDef(Instant start, Instant end, String label) {}

    private record Window(Instant start, Instant end, List<BucketDef> buckets) {}

    @Override
    public SellerDetailDTO getDetail(String email) {
        List<Order> orders = new ArrayList<>(orderRepository.findByEmail(email));
        orders.sort(Comparator.comparing(Order::getSaleDate, Comparator.nullsLast(Comparator.reverseOrder())));

        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();

        Order lastOrder = orders.isEmpty() ? null : orders.get(0);
        Integer daysSinceLastOrder = lastOrder == null
            ? null
            : (int) ChronoUnit.DAYS.between(lastOrder.getSaleDate(), now);

        String firstSaleTime = null;
        String lastSaleTime = null;
        String lastActiveDayLabel = null;
        if (lastOrder != null) {
            LocalDate lastActiveDay = lastOrder.getSaleDate().atZone(zone).toLocalDate();
            List<Order> ordersOnThatDay = orders.stream()
                .filter(o -> o.getSaleDate().atZone(zone).toLocalDate().equals(lastActiveDay))
                .sorted(Comparator.comparing(Order::getSaleDate))
                .toList();
            firstSaleTime = ordersOnThatDay.get(0).getSaleDate().atZone(zone).format(HOUR_LABEL);
            lastSaleTime = ordersOnThatDay.get(ordersOnThatDay.size() - 1).getSaleDate().atZone(zone).format(HOUR_LABEL);
            lastActiveDayLabel = lastActiveDay.equals(LocalDate.now(zone))
                ? "Aujourd'hui"
                : lastActiveDay.format(DAY_LABEL);
        }

        List<SellerDetailDTO.SellerPeriodDTO> periods = new ArrayList<>();
        for (String key : List.of("7j", "4sem", "6mois", "2ans")) {
            periods.add(buildPeriod(key, orders, now, zone));
        }

        String defaultPeriodKey = periods.stream()
            .filter(p -> "7j".equals(p.key()))
            .findFirst()
            .filter(p -> p.transactionsCount() > 0)
            .map(SellerDetailDTO.SellerPeriodDTO::key)
            .orElse("6mois");

        SellerDetailDTO.SellerAlertDTO alert = resolveAlert(orders, daysSinceLastOrder, now, zone);

        return new SellerDetailDTO(
            email,
            alert,
            firstSaleTime,
            lastSaleTime,
            lastActiveDayLabel,
            periods,
            defaultPeriodKey
        );
    }

    private SellerDetailDTO.SellerAlertDTO resolveAlert(
        List<Order> orders,
        Integer daysSinceLastOrder,
        Instant now,
        ZoneId zone
    ) {
        if (daysSinceLastOrder != null && daysSinceLastOrder >= INACTIVE_DAYS_CRITICAL) {
            return new SellerDetailDTO.SellerAlertDTO(
                "critical",
                "Aucune vente depuis " + daysSinceLastOrder + " jours"
            );
        }

        LocalDate today = LocalDate.now(zone);
        Instant currentStart = today.minusDays(6).atStartOfDay(zone).toInstant();
        Instant currentEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant previousStart = today.minusDays(13).atStartOfDay(zone).toInstant();
        Instant previousEnd = currentStart;

        BigDecimal currentCa = sumCaInRange(orders, currentStart, currentEnd);
        BigDecimal previousCa = sumCaInRange(orders, previousStart, previousEnd);

        if (previousCa.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dropPct = previousCa
                .subtract(currentCa)
                .divide(previousCa, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            if (dropPct.compareTo(CADENCE_DROP_WARNING_PCT) > 0) {
                return new SellerDetailDTO.SellerAlertDTO(
                    "warning",
                    "Cadence en baisse de " + dropPct.setScale(0, RoundingMode.HALF_UP)
                        + "% vs la semaine dernière"
                );
            }
        }

        return null;
    }

    private BigDecimal sumCaInRange(List<Order> orders, Instant start, Instant endExclusive) {
        BigDecimal total = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order.getSaleDate() != null
                && !order.getSaleDate().isBefore(start)
                && order.getSaleDate().isBefore(endExclusive)) {
                total = total.add(OrderMath.computeOrderCa(order));
            }
        }
        return total;
    }

    private SellerDetailDTO.SellerPeriodDTO buildPeriod(
        String key,
        List<Order> allOrders,
        Instant now,
        ZoneId zone
    ) {
        Window window = resolveWindow(key, now, zone);

        List<Order> inPeriod = allOrders.stream()
            .filter(o -> o.getSaleDate() != null
                && !o.getSaleDate().isBefore(window.start())
                && o.getSaleDate().isBefore(window.end()))
            .toList();

        BigDecimal totalCa = BigDecimal.ZERO;
        BigDecimal totalBenefice = BigDecimal.ZERO;
        int itemsSoldCount = 0;
        Map<Integer, int[]> hourlyCounts = new LinkedHashMap<>();
        Map<Integer, BigDecimal> hourlyCa = new LinkedHashMap<>();
        Map<String, int[]> itemQty = new LinkedHashMap<>();
        Map<String, BigDecimal> itemCa = new LinkedHashMap<>();
        int bucketsSize = window.buckets().size();
        BigDecimal[] bucketCa = new BigDecimal[bucketsSize];
        BigDecimal[] bucketBenefice = new BigDecimal[bucketsSize];
        int[] bucketCount = new int[bucketsSize];
        Instant[] bucketFirstSale = new Instant[bucketsSize];
        Instant[] bucketLastSale = new Instant[bucketsSize];
        List<Map<Integer, int[]>> bucketHourlyCounts = new ArrayList<>();
        List<Map<Integer, BigDecimal>> bucketHourlyCa = new ArrayList<>();
        for (int i = 0; i < bucketsSize; i++) {
            bucketCa[i] = BigDecimal.ZERO;
            bucketBenefice[i] = BigDecimal.ZERO;
            bucketHourlyCounts.add(new LinkedHashMap<>());
            bucketHourlyCa.add(new LinkedHashMap<>());
        }

        for (Order order : inPeriod) {
            BigDecimal orderCa = OrderMath.computeOrderCa(order);
            BigDecimal orderBenefice = order.getDelta() != null ? order.getDelta() : BigDecimal.ZERO;
            totalCa = totalCa.add(orderCa);
            totalBenefice = totalBenefice.add(orderBenefice);

            int hour = order.getSaleDate().atZone(zone).getHour();
            hourlyCounts.computeIfAbsent(hour, h -> new int[1])[0]++;
            hourlyCa.merge(hour, orderCa, BigDecimal::add);

            int bucketIndex = findBucketIndex(window.buckets(), order.getSaleDate());
            if (bucketIndex >= 0) {
                bucketCa[bucketIndex] = bucketCa[bucketIndex].add(orderCa);
                bucketBenefice[bucketIndex] = bucketBenefice[bucketIndex].add(orderBenefice);
                bucketCount[bucketIndex]++;

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

            if (order.getArticles() != null) {
                for (OrderItem item : order.getArticles()) {
                    itemsSoldCount += item.getQuantityOrdered();
                    BigDecimal lineCa = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantityOrdered()));
                    itemQty.computeIfAbsent(item.getName(), n -> new int[1])[0] += item.getQuantityOrdered();
                    itemCa.merge(item.getName(), lineCa, BigDecimal::add);
                }
            }
        }

        List<SellerDetailDTO.ActivityPointDTO> activity = new ArrayList<>();
        int activeBuckets = 0;
        for (int i = 0; i < bucketsSize; i++) {
            final int bucketIdx = i;
            boolean active = bucketCount[i] > 0;
            if (active) {
                activeBuckets++;
            }
            activity.add(new SellerDetailDTO.ActivityPointDTO(
                window.buckets().get(i).label(),
                round(bucketCa[i]),
                round(bucketBenefice[i]),
                bucketCount[i],
                active,
                bucketFirstSale[i] == null ? null : bucketFirstSale[i].atZone(zone).format(HOUR_LABEL),
                bucketLastSale[i] == null ? null : bucketLastSale[i].atZone(zone).format(HOUR_LABEL),
                bucketHourlyCounts.get(i).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new SellerDetailDTO.HourlyPointDTO(
                        e.getKey(),
                        e.getValue()[0],
                        round(bucketHourlyCa.get(bucketIdx).getOrDefault(e.getKey(), BigDecimal.ZERO))
                    ))
                    .toList()
            ));
        }

        List<SellerDetailDTO.HourlyPointDTO> hourlyPattern = hourlyCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new SellerDetailDTO.HourlyPointDTO(
                e.getKey(),
                e.getValue()[0],
                round(hourlyCa.getOrDefault(e.getKey(), BigDecimal.ZERO))
            ))
            .toList();

        List<SellerDetailDTO.TopItemDTO> topItems = itemCa.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(TOP_ITEMS_LIMIT)
            .map(e -> new SellerDetailDTO.TopItemDTO(
                e.getKey(),
                null,
                itemQty.get(e.getKey())[0],
                round(e.getValue())
            ))
            .toList();

        int transactionsCount = inPeriod.size();
        BigDecimal averageBasket = transactionsCount == 0
            ? BigDecimal.ZERO
            : round(totalCa.divide(BigDecimal.valueOf(transactionsCount), 4, RoundingMode.HALF_UP));

        return new SellerDetailDTO.SellerPeriodDTO(
            key,
            periodLabel(key),
            round(totalCa),
            round(totalBenefice),
            averageBasket,
            transactionsCount,
            itemsSoldCount,
            activeBuckets,
            window.buckets().size(),
            activity,
            hourlyPattern,
            topItems
        );
    }

    private Window resolveWindow(String key, Instant now, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return switch (key) {
            case "7j" -> dailyWindow(today, zone, 7);
            case "4sem" -> weeklyWindow(today, zone, 4);
            case "6mois" -> monthlyWindow(today, zone, 6);
            case "2ans" -> yearlyWindow(today, zone, 2);
            default -> throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "Période inconnue : " + key
            );
        };
    }

    private Window dailyWindow(LocalDate today, ZoneId zone, int count) {
        LocalDate firstDay = today.minusDays(count - 1L);
        List<BucketDef> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate day = firstDay.plusDays(i);
            Instant start = day.atStartOfDay(zone).toInstant();
            Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
            buckets.add(new BucketDef(start, end, day.format(DAY_LABEL)));
        }
        return new Window(buckets.get(0).start(), buckets.get(buckets.size() - 1).end(), buckets);
    }

    private Window weeklyWindow(LocalDate today, ZoneId zone, int count) {
        LocalDate mondayThisWeek = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate firstMonday = mondayThisWeek.minusWeeks(count - 1L);
        List<BucketDef> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate weekStart = firstMonday.plusWeeks(i);
            Instant start = weekStart.atStartOfDay(zone).toInstant();
            Instant end = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant();
            buckets.add(new BucketDef(start, end, "Sem " + (i + 1)));
        }
        return new Window(buckets.get(0).start(), buckets.get(buckets.size() - 1).end(), buckets);
    }

    private Window monthlyWindow(LocalDate today, ZoneId zone, int count) {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = currentMonth.minusMonths(count - 1L);
        List<BucketDef> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
            Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
            buckets.add(new BucketDef(start, end, month.atDay(1).format(MONTH_LABEL)));
        }
        return new Window(buckets.get(0).start(), buckets.get(buckets.size() - 1).end(), buckets);
    }

    private Window yearlyWindow(LocalDate today, ZoneId zone, int count) {
        int currentYear = today.getYear();
        int firstYear = currentYear - (count - 1);
        List<BucketDef> buckets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int year = firstYear + i;
            Instant start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant();
            Instant end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant();
            buckets.add(new BucketDef(start, end, String.valueOf(year)));
        }
        return new Window(buckets.get(0).start(), buckets.get(buckets.size() - 1).end(), buckets);
    }

    private int findBucketIndex(List<BucketDef> buckets, Instant saleDate) {
        for (int i = 0; i < buckets.size(); i++) {
            BucketDef bucket = buckets.get(i);
            if (!saleDate.isBefore(bucket.start()) && saleDate.isBefore(bucket.end())) {
                return i;
            }
        }
        return -1;
    }

    private String periodLabel(String key) {
        return switch (key) {
            case "7j" -> "7 jours";
            case "4sem" -> "4 semaines";
            case "6mois" -> "6 mois";
            case "2ans" -> "2 ans";
            default -> key;
        };
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
