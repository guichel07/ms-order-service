package org.acme.Service.Client.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
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
import java.util.Optional;
import org.acme.DTO.ClientDetailDTO;
import org.acme.Entity.Client;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Client.ClientDetailService;
import org.acme.Util.OrderMath;
import org.bson.types.ObjectId;

/**
 * Construit l'agrégat complet d'une fiche client (les 4 périodes en une seule
 * réponse — voir la discussion avec le front sur la charge utile : on envoie tout
 * d'un coup plutôt que du lazy-loading, les requêtes restent bornées par client).
 */
@ApplicationScoped
public class ClientDetailServiceImpl implements ClientDetailService {

    private static final BigDecimal VIP_CA_THRESHOLD = BigDecimal.valueOf(100_000);
    private static final int VIP_ORDERS_THRESHOLD = 20;
    private static final int NOUVEAU_MAX_ORDERS = 2;
    private static final long NOUVEAU_MAX_DAYS = 30;
    private static final long AT_RISK_DAYS_VIP = 30;
    private static final long AT_RISK_DAYS_REGULIER = 45;
    private static final int TOP_PRODUCTS_LIMIT = 3;
    private static final int ORDER_HISTORY_LIMIT = 20;

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH);
    private static final DateTimeFormatter SINCE_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);

    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;

    public ClientDetailServiceImpl(ClientRepository _clientRepository, OrderRepository _orderRepository) {
        this.clientRepository = _clientRepository;
        this.orderRepository = _orderRepository;
    }

    private record BucketDef(Instant start, Instant end, String label) {}

    private record Window(Instant start, Instant end, List<BucketDef> buckets) {}

    @Override
    public ClientDetailDTO getDetail(String clientId) {
        Client client = Optional.ofNullable(clientRepository.findById(new ObjectId(clientId)))
            .orElseThrow(() -> new BusinessException(Response.Status.NOT_FOUND, "Client not found"));

        List<Order> orders = new ArrayList<>(orderRepository.findByClientId(clientId));
        orders.sort(Comparator.comparing(Order::getSaleDate, Comparator.nullsLast(Comparator.reverseOrder())));

        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();

        Order lastOrder = orders.isEmpty() ? null : orders.get(0);
        Order firstOrder = orders.isEmpty() ? null : orders.get(orders.size() - 1);

        BigDecimal lifetimeCa = BigDecimal.ZERO;
        for (Order order : orders) {
            lifetimeCa = lifetimeCa.add(OrderMath.computeOrderCa(order));
        }

        String segment = resolveSegment(orders.size(), lifetimeCa, firstOrder, now);
        Integer daysSinceLastOrder = lastOrder == null
            ? null
            : (int) ChronoUnit.DAYS.between(lastOrder.getSaleDate(), now);
        boolean isAtRisk = resolveAtRisk(segment, daysSinceLastOrder);

        String clientSinceLabel = client.isAnonymous()
            ? "Ventes anonymes cumulées"
            : firstOrder == null
                ? "Nouveau client"
                : "Client depuis " + firstOrder.getSaleDate().atZone(zone).format(SINCE_LABEL);

        List<ClientDetailDTO.PeriodDTO> periods = new ArrayList<>();
        for (String key : List.of("7j", "4sem", "6mois", "2ans")) {
            periods.add(buildPeriod(key, orders, now, zone));
        }

        String defaultPeriodKey = periods.stream()
            .filter(p -> "6mois".equals(p.key()))
            .findFirst()
            .filter(p -> p.ordersCount() > 0)
            .map(ClientDetailDTO.PeriodDTO::key)
            .orElse("2ans");

        return new ClientDetailDTO(
            client.id.toHexString(),
            client.getFirstname(),
            client.getLastname(),
            client.getPhone(),
            client.getAgeCategory(),
            client.getGender(),
            client.isAnonymous(),
            segment,
            isAtRisk,
            daysSinceLastOrder,
            clientSinceLabel,
            periods,
            defaultPeriodKey
        );
    }

    private ClientDetailDTO.PeriodDTO buildPeriod(String key, List<Order> allOrders, Instant now, ZoneId zone) {
        Window window = resolveWindow(key, now, zone);

        List<Order> inPeriod = allOrders.stream()
            .filter(o -> o.getSaleDate() != null
                && !o.getSaleDate().isBefore(window.start())
                && o.getSaleDate().isBefore(window.end()))
            .sorted(Comparator.comparing(Order::getSaleDate, Comparator.reverseOrder()))
            .toList();

        BigDecimal totalCa = BigDecimal.ZERO;
        BigDecimal totalBenefice = BigDecimal.ZERO;
        Map<Integer, int[]> hourlyOrderCounts = new LinkedHashMap<>();
        Map<Integer, BigDecimal> hourlyCa = new LinkedHashMap<>();
        Map<String, int[]> productQty = new LinkedHashMap<>();
        Map<String, BigDecimal> productCa = new LinkedHashMap<>();
        Map<String, String> productIcon = new LinkedHashMap<>();
        BigDecimal[] bucketCa = new BigDecimal[window.buckets().size()];
        BigDecimal[] bucketBenefice = new BigDecimal[window.buckets().size()];
        for (int i = 0; i < bucketCa.length; i++) {
            bucketCa[i] = BigDecimal.ZERO;
            bucketBenefice[i] = BigDecimal.ZERO;
        }

        for (Order order : inPeriod) {
            BigDecimal orderCa = OrderMath.computeOrderCa(order);
            BigDecimal orderBenefice = order.getDelta() != null ? order.getDelta() : BigDecimal.ZERO;
            totalCa = totalCa.add(orderCa);
            totalBenefice = totalBenefice.add(orderBenefice);

            int hour = order.getSaleDate().atZone(zone).getHour();
            hourlyOrderCounts.computeIfAbsent(hour, h -> new int[1])[0]++;
            hourlyCa.merge(hour, orderCa, BigDecimal::add);

            int bucketIndex = findBucketIndex(window.buckets(), order.getSaleDate());
            if (bucketIndex >= 0) {
                bucketCa[bucketIndex] = bucketCa[bucketIndex].add(orderCa);
                bucketBenefice[bucketIndex] = bucketBenefice[bucketIndex].add(orderBenefice);
            }

            if (order.getArticles() != null) {
                for (OrderItem item : order.getArticles()) {
                    BigDecimal itemCa = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantityOrdered()));
                    productQty.computeIfAbsent(item.getName(), n -> new int[1])[0] += item.getQuantityOrdered();
                    productCa.merge(item.getName(), itemCa, BigDecimal::add);
                    productIcon.putIfAbsent(item.getName(), null);
                }
            }
        }

        List<ClientDetailDTO.MonthlyPointDTO> monthlyTrend = new ArrayList<>();
        for (int i = 0; i < window.buckets().size(); i++) {
            monthlyTrend.add(new ClientDetailDTO.MonthlyPointDTO(
                window.buckets().get(i).label(),
                round(bucketCa[i]),
                round(bucketBenefice[i])
            ));
        }

        List<ClientDetailDTO.HourlyPointDTO> hourlyPattern = hourlyOrderCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new ClientDetailDTO.HourlyPointDTO(
                e.getKey(),
                e.getValue()[0],
                round(hourlyCa.getOrDefault(e.getKey(), BigDecimal.ZERO))
            ))
            .toList();

        List<ClientDetailDTO.TopProductDTO> topProducts = productCa.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(TOP_PRODUCTS_LIMIT)
            .map(e -> new ClientDetailDTO.TopProductDTO(
                e.getKey(),
                productIcon.get(e.getKey()),
                productQty.get(e.getKey())[0],
                round(e.getValue())
            ))
            .toList();

        List<ClientDetailDTO.OrderSummaryDTO> orderHistory = inPeriod.stream()
            .limit(ORDER_HISTORY_LIMIT)
            .map(order -> toOrderSummary(order, zone))
            .toList();

        int ordersCount = inPeriod.size();
        BigDecimal averageOrderValue = ordersCount == 0
            ? BigDecimal.ZERO
            : round(totalCa.divide(BigDecimal.valueOf(ordersCount), 4, RoundingMode.HALF_UP));

        return new ClientDetailDTO.PeriodDTO(
            key,
            periodLabel(key),
            round(totalCa),
            round(totalBenefice),
            averageOrderValue,
            ordersCount,
            purchaseRhythmLabel(inPeriod),
            monthlyTrend,
            hourlyPattern,
            topProducts,
            orderHistory
        );
    }

    private ClientDetailDTO.OrderSummaryDTO toOrderSummary(Order order, ZoneId zone) {
        List<OrderItem> items = order.getArticles() != null ? order.getArticles() : List.of();
        String firstItem = items.isEmpty()
            ? "—"
            : items.get(0).getName() + " ×" + items.get(0).getQuantityOrdered();
        String extraItems = items.size() > 1 ? "+ " + (items.size() - 1) + " autre(s) article(s)" : null;

        return new ClientDetailDTO.OrderSummaryDTO(
            order.getSaleDate().atZone(zone).format(DAY_LABEL),
            firstItem,
            extraItems,
            round(OrderMath.computeOrderCa(order)),
            round(order.getDelta() != null ? order.getDelta() : BigDecimal.ZERO)
        );
    }

    private String purchaseRhythmLabel(List<Order> ordersDesc) {
        if (ordersDesc.size() < 2) {
            return "—";
        }
        Instant mostRecent = ordersDesc.get(0).getSaleDate();
        Instant oldest = ordersDesc.get(ordersDesc.size() - 1).getSaleDate();
        long spanDays = ChronoUnit.DAYS.between(oldest, mostRecent);
        long averageGap = Math.max(1, Math.round((double) spanDays / (ordersDesc.size() - 1)));
        return "~" + averageGap + "j";
    }

    private String resolveSegment(int lifetimeOrdersCount, BigDecimal lifetimeCa, Order firstOrder, Instant now) {
        boolean recentFirstOrder = firstOrder != null
            && ChronoUnit.DAYS.between(firstOrder.getSaleDate(), now) <= NOUVEAU_MAX_DAYS;

        if (lifetimeOrdersCount <= NOUVEAU_MAX_ORDERS && recentFirstOrder) {
            return "nouveau";
        }
        if (lifetimeCa.compareTo(VIP_CA_THRESHOLD) >= 0 || lifetimeOrdersCount >= VIP_ORDERS_THRESHOLD) {
            return "vip";
        }
        if (lifetimeOrdersCount <= NOUVEAU_MAX_ORDERS) {
            return "occasionnel";
        }
        return "regulier";
    }

    private boolean resolveAtRisk(String segment, Integer daysSinceLastOrder) {
        if (daysSinceLastOrder == null || (!"vip".equals(segment) && !"regulier".equals(segment))) {
            return false;
        }
        long threshold = "vip".equals(segment) ? AT_RISK_DAYS_VIP : AT_RISK_DAYS_REGULIER;
        return daysSinceLastOrder > threshold;
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
