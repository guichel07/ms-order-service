package org.acme.Service.Client.impl;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.acme.DTO.ClientsOverviewDTO;
import org.acme.Entity.Client;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Repository.ClientRepository;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Client.ClientsOverviewService;
import org.acme.Util.OrderMath;

/**
 * Construit l'agrégat complet de l'onglet "Analyse" pour le mois courant — en un
 * seul appel (même choix que ClientDetailServiceImpl : pas de lazy-loading, la
 * volumétrie actuelle (~1500 commandes) reste largement gérable en mémoire).
 */
@ApplicationScoped
public class ClientsOverviewServiceImpl implements ClientsOverviewService {

    private static final BigDecimal VIP_CA_THRESHOLD = BigDecimal.valueOf(100_000);
    private static final int VIP_ORDERS_THRESHOLD = 20;
    private static final int NOUVEAU_MAX_ORDERS = 2;
    private static final long AT_RISK_DAYS_VIP = 30;
    private static final long AT_RISK_DAYS_REGULIER = 45;
    private static final int TOP_CLIENTS_LIMIT = 5;
    private static final int AT_RISK_LIMIT = 5;
    private static final int TOP_PRODUCTS_LIMIT = 3;
    private static final int SLOT_TOP_PRODUCTS_LIMIT = 2;
    private static final int COMBOS_LIMIT = 3;

    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;

    public ClientsOverviewServiceImpl(ClientRepository _clientRepository, OrderRepository _orderRepository) {
        this.clientRepository = _clientRepository;
        this.orderRepository = _orderRepository;
    }

    private record ClientLifetime(
        String clientId,
        BigDecimal totalCa,
        int ordersCount,
        Instant firstOrderDate,
        Instant lastOrderDate
    ) {}

    @Override
    public ClientsOverviewDTO getOverview() {
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zone);
        YearMonth currentMonth = YearMonth.from(today);
        Instant monthStart = currentMonth.atDay(1).atStartOfDay(zone).toInstant();
        Instant monthEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        List<Order> allOrders = orderRepository.listAll();
        List<Order> monthOrders = allOrders.stream()
            .filter(o -> o.getSaleDate() != null && !o.getSaleDate().isBefore(monthStart) && o.getSaleDate().isBefore(monthEnd))
            .toList();

        Map<String, Client> clientsById = new HashMap<>();
        for (Client client : clientRepository.listAll()) {
            clientsById.put(client.id.toHexString(), client);
        }

        Map<String, ClientLifetime> lifetimeByClient = buildLifetimeByClient(allOrders);

        return new ClientsOverviewDTO(
            buildHourlyTraffic(monthOrders, zone),
            buildDayOfMonthTrend(monthOrders, zone, currentMonth),
            buildNewVsReturning(monthOrders, lifetimeByClient, monthStart),
            buildTopClients(lifetimeByClient, clientsById),
            buildTopVipProducts(monthOrders, lifetimeByClient, clientsById),
            buildAtRiskClients(lifetimeByClient, clientsById, now),
            buildTimeSlots(monthOrders),
            buildFrequentCombos(monthOrders)
        );
    }

    private Map<String, ClientLifetime> buildLifetimeByClient(List<Order> allOrders) {
        Map<String, BigDecimal> ca = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        Map<String, Instant> first = new HashMap<>();
        Map<String, Instant> last = new HashMap<>();

        for (Order order : allOrders) {
            String clientId = order.getClientId();
            if (clientId == null || order.getSaleDate() == null) {
                continue;
            }
            ca.merge(clientId, OrderMath.computeOrderCa(order), BigDecimal::add);
            count.merge(clientId, 1, Integer::sum);
            first.merge(clientId, order.getSaleDate(), (a, b) -> a.isBefore(b) ? a : b);
            last.merge(clientId, order.getSaleDate(), (a, b) -> a.isAfter(b) ? a : b);
        }

        Map<String, ClientLifetime> result = new HashMap<>();
        for (String clientId : ca.keySet()) {
            result.put(clientId, new ClientLifetime(
                clientId,
                ca.get(clientId),
                count.get(clientId),
                first.get(clientId),
                last.get(clientId)
            ));
        }
        return result;
    }

    private List<ClientsOverviewDTO.HourlyTrafficPointDTO> buildHourlyTraffic(List<Order> orders, ZoneId zone) {
        int[] counts = new int[24];
        BigDecimal[] cas = new BigDecimal[24];
        for (int i = 0; i < 24; i++) {
            cas[i] = BigDecimal.ZERO;
        }
        for (Order order : orders) {
            int hour = order.getSaleDate().atZone(zone).getHour();
            counts[hour]++;
            cas[hour] = cas[hour].add(OrderMath.computeOrderCa(order));
        }
        List<ClientsOverviewDTO.HourlyTrafficPointDTO> points = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            points.add(new ClientsOverviewDTO.HourlyTrafficPointDTO(hour, counts[hour], round(cas[hour])));
        }
        return points;
    }

    private List<ClientsOverviewDTO.DayOfMonthPointDTO> buildDayOfMonthTrend(
        List<Order> monthOrders,
        ZoneId zone,
        YearMonth currentMonth
    ) {
        int daysInMonth = currentMonth.lengthOfMonth();
        Map<Integer, List<Order>> ordersByDay = new LinkedHashMap<>();
        for (Order order : monthOrders) {
            int day = order.getSaleDate().atZone(zone).getDayOfMonth();
            ordersByDay.computeIfAbsent(day, d -> new ArrayList<>()).add(order);
        }

        List<ClientsOverviewDTO.DayOfMonthPointDTO> days = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            List<Order> dayOrders = ordersByDay.getOrDefault(day, List.of());
            BigDecimal dayCa = BigDecimal.ZERO;
            for (Order order : dayOrders) {
                dayCa = dayCa.add(OrderMath.computeOrderCa(order));
            }
            days.add(new ClientsOverviewDTO.DayOfMonthPointDTO(
                day,
                round(dayCa),
                buildHourlyTraffic(dayOrders, zone)
            ));
        }
        return days;
    }

    private ClientsOverviewDTO.NewVsReturningSplitDTO buildNewVsReturning(
        List<Order> monthOrders,
        Map<String, ClientLifetime> lifetimeByClient,
        Instant monthStart
    ) {
        Map<String, BigDecimal> caByClient = new HashMap<>();
        for (Order order : monthOrders) {
            if (order.getClientId() == null) {
                continue;
            }
            caByClient.merge(order.getClientId(), OrderMath.computeOrderCa(order), BigDecimal::add);
        }

        BigDecimal newCa = BigDecimal.ZERO;
        BigDecimal returningCa = BigDecimal.ZERO;
        int newCount = 0;
        int returningCount = 0;

        for (Map.Entry<String, BigDecimal> entry : caByClient.entrySet()) {
            ClientLifetime lifetime = lifetimeByClient.get(entry.getKey());
            boolean isNew = lifetime == null || !lifetime.firstOrderDate().isBefore(monthStart);
            if (isNew) {
                newCa = newCa.add(entry.getValue());
                newCount++;
            } else {
                returningCa = returningCa.add(entry.getValue());
                returningCount++;
            }
        }

        return new ClientsOverviewDTO.NewVsReturningSplitDTO(round(newCa), round(returningCa), newCount, returningCount);
    }

    private List<ClientsOverviewDTO.TopClientDTO> buildTopClients(
        Map<String, ClientLifetime> lifetimeByClient,
        Map<String, Client> clientsById
    ) {
        return lifetimeByClient.values().stream()
            .filter(l -> clientsById.containsKey(l.clientId()))
            .sorted(Comparator.comparing(ClientLifetime::totalCa).reversed())
            .limit(TOP_CLIENTS_LIMIT)
            .map(l -> {
                Client client = clientsById.get(l.clientId());
                String segment = resolveSegment(l.ordersCount(), l.totalCa());
                BigDecimal average = l.ordersCount() == 0
                    ? BigDecimal.ZERO
                    : round(l.totalCa().divide(BigDecimal.valueOf(l.ordersCount()), 4, RoundingMode.HALF_UP));
                return new ClientsOverviewDTO.TopClientDTO(
                    l.clientId(),
                    client.getFirstname(),
                    client.getLastname(),
                    round(l.totalCa()),
                    average,
                    segment
                );
            })
            .toList();
    }

    private List<ClientsOverviewDTO.TopProductDTO> buildTopVipProducts(
        List<Order> monthOrders,
        Map<String, ClientLifetime> lifetimeByClient,
        Map<String, Client> clientsById
    ) {
        return aggregateTopProducts(
            monthOrders.stream()
                .filter(o -> o.getClientId() != null && isVip(lifetimeByClient.get(o.getClientId())))
                .toList(),
            TOP_PRODUCTS_LIMIT
        );
    }

    private boolean isVip(ClientLifetime lifetime) {
        return lifetime != null && "vip".equals(resolveSegment(lifetime.ordersCount(), lifetime.totalCa()));
    }

    private List<ClientsOverviewDTO.AtRiskClientDTO> buildAtRiskClients(
        Map<String, ClientLifetime> lifetimeByClient,
        Map<String, Client> clientsById,
        Instant now
    ) {
        return lifetimeByClient.values().stream()
            .filter(l -> clientsById.containsKey(l.clientId()))
            .map(l -> {
                String segment = resolveSegment(l.ordersCount(), l.totalCa());
                int daysSinceLastOrder = (int) ChronoUnit.DAYS.between(l.lastOrderDate(), now);
                return Map.entry(l, Map.entry(segment, daysSinceLastOrder));
            })
            .filter(e -> resolveAtRisk(e.getValue().getKey(), e.getValue().getValue()))
            .sorted((a, b) -> Integer.compare(b.getValue().getValue(), a.getValue().getValue()))
            .limit(AT_RISK_LIMIT)
            .map(e -> {
                Client client = clientsById.get(e.getKey().clientId());
                return new ClientsOverviewDTO.AtRiskClientDTO(
                    e.getKey().clientId(),
                    client.getFirstname(),
                    client.getLastname(),
                    e.getValue().getKey(),
                    e.getValue().getValue()
                );
            })
            .toList();
    }

    private List<ClientsOverviewDTO.TimeSlotSummaryDTO> buildTimeSlots(List<Order> monthOrders) {
        List<ClientsOverviewDTO.TimeSlotSummaryDTO> slots = new ArrayList<>();
        slots.add(buildTimeSlot("matin", "Matin (6h-11h)", monthOrders, 6, 11));
        slots.add(buildTimeSlot("midi", "Midi (11h-15h)", monthOrders, 11, 15));
        slots.add(buildTimeSlot("soir", "Soir (15h-20h)", monthOrders, 15, 20));
        return slots;
    }

    private ClientsOverviewDTO.TimeSlotSummaryDTO buildTimeSlot(
        String key,
        String label,
        List<Order> monthOrders,
        int startHourInclusive,
        int endHourExclusive
    ) {
        ZoneId zone = ZoneId.systemDefault();
        List<Order> slotOrders = monthOrders.stream()
            .filter(o -> {
                int hour = o.getSaleDate().atZone(zone).getHour();
                return hour >= startHourInclusive && hour < endHourExclusive;
            })
            .toList();

        BigDecimal totalCa = BigDecimal.ZERO;
        for (Order order : slotOrders) {
            totalCa = totalCa.add(OrderMath.computeOrderCa(order));
        }
        BigDecimal average = slotOrders.isEmpty()
            ? BigDecimal.ZERO
            : round(totalCa.divide(BigDecimal.valueOf(slotOrders.size()), 4, RoundingMode.HALF_UP));

        return new ClientsOverviewDTO.TimeSlotSummaryDTO(
            key,
            label,
            average,
            aggregateTopProducts(slotOrders, SLOT_TOP_PRODUCTS_LIMIT)
        );
    }

    private List<ClientsOverviewDTO.ProductComboDTO> buildFrequentCombos(List<Order> monthOrders) {
        Map<String, Integer> comboCounts = new LinkedHashMap<>();
        for (Order order : monthOrders) {
            List<OrderItem> items = order.getArticles();
            if (items == null || items.size() < 2) {
                continue;
            }
            List<String> names = items.stream().map(OrderItem::getName).distinct().sorted().toList();
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    String comboKey = names.get(i) + "||" + names.get(j);
                    comboCounts.merge(comboKey, 1, Integer::sum);
                }
            }
        }

        return comboCounts.entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(COMBOS_LIMIT)
            .map(e -> {
                String[] products = e.getKey().split("\\|\\|");
                return new ClientsOverviewDTO.ProductComboDTO(List.of(products), null, e.getValue());
            })
            .toList();
    }

    private List<ClientsOverviewDTO.TopProductDTO> aggregateTopProducts(List<Order> orders, int limit) {
        Map<String, Integer> qty = new LinkedHashMap<>();
        Map<String, BigDecimal> ca = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getArticles() == null) {
                continue;
            }
            for (OrderItem item : order.getArticles()) {
                BigDecimal itemCa = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantityOrdered()));
                qty.merge(item.getName(), item.getQuantityOrdered(), Integer::sum);
                ca.merge(item.getName(), itemCa, BigDecimal::add);
            }
        }
        return ca.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(limit)
            .map(e -> new ClientsOverviewDTO.TopProductDTO(e.getKey(), null, qty.get(e.getKey()), round(e.getValue())))
            .toList();
    }

    private String resolveSegment(int lifetimeOrdersCount, BigDecimal lifetimeCa) {
        if (lifetimeCa.compareTo(VIP_CA_THRESHOLD) >= 0 || lifetimeOrdersCount >= VIP_ORDERS_THRESHOLD) {
            return "vip";
        }
        if (lifetimeOrdersCount <= NOUVEAU_MAX_ORDERS) {
            return "occasionnel";
        }
        return "regulier";
    }

    private boolean resolveAtRisk(String segment, int daysSinceLastOrder) {
        if (!"vip".equals(segment) && !"regulier".equals(segment)) {
            return false;
        }
        long threshold = "vip".equals(segment) ? AT_RISK_DAYS_VIP : AT_RISK_DAYS_REGULIER;
        return daysSinceLastOrder > threshold;
    }


    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
