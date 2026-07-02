package org.acme.Service.Article.impl;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.acme.DTO.ArticleDetailDTO;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Article.ArticleDetailService;
import jakarta.ws.rs.core.Response;

/**
 * Construit l'agrégat performance d'un article (les 4 périodes en une seule réponse).
 * Volontairement plus simple que SellerDetailServiceImpl : pas de découpage par bucket
 * ni horaire — juste les totaux de chaque période, la vélocité, et une alerte si le
 * rythme de vente ralentit. Vue admin/comptable, pas vue terrain.
 */
@ApplicationScoped
public class ArticleDetailServiceImpl implements ArticleDetailService {

    /** Baisse de vélocité (7 derniers jours vs 7 jours précédents) au-delà de ce seuil -> alerte. */
    private static final BigDecimal VELOCITY_DROP_WARNING_PCT = BigDecimal.valueOf(20);
    /** En dessous, une association est probablement une coïncidence (2 commandes ensemble par hasard). */
    private static final int MIN_CO_OCCURRENCE = 2;
    private static final int TOP_ASSOCIATED_LIMIT = 3;

    private final OrderRepository orderRepository;

    public ArticleDetailServiceImpl(OrderRepository _orderRepository) {
        this.orderRepository = _orderRepository;
    }

    private record Window(Instant start, Instant end) {}

    @Override
    public ArticleDetailDTO getDetail(String articleId) {
        List<Order> orders = orderRepository.findByArticleId(articleId);

        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();

        List<ArticleDetailDTO.ArticlePeriodDTO> periods = new ArrayList<>();
        for (String key : List.of("7j", "4sem", "6mois", "2ans")) {
            periods.add(buildPeriod(key, articleId, orders, now, zone));
        }

        String defaultPeriodKey = periods.stream()
            .filter(p -> "7j".equals(p.key()))
            .findFirst()
            .filter(p -> p.transactionsCount() > 0)
            .map(ArticleDetailDTO.ArticlePeriodDTO::key)
            .orElse("6mois");

        ArticleDetailDTO.ArticleAlertDTO alert = resolveAlert(articleId, orders, now, zone);

        return new ArticleDetailDTO(articleId, alert, periods, defaultPeriodKey);
    }

    private ArticleDetailDTO.ArticleAlertDTO resolveAlert(
        String articleId,
        List<Order> orders,
        Instant now,
        ZoneId zone
    ) {
        LocalDate today = LocalDate.now(zone);
        Instant currentStart = today.minusDays(6).atStartOfDay(zone).toInstant();
        Instant currentEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant previousStart = today.minusDays(13).atStartOfDay(zone).toInstant();
        Instant previousEnd = currentStart;

        BigDecimal currentQty = sumQuantityInRange(articleId, orders, currentStart, currentEnd);
        BigDecimal previousQty = sumQuantityInRange(articleId, orders, previousStart, previousEnd);

        if (previousQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dropPct = previousQty.subtract(currentQty)
                .divide(previousQty, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            if (dropPct.compareTo(VELOCITY_DROP_WARNING_PCT) > 0) {
                return new ArticleDetailDTO.ArticleAlertDTO(
                    "warning",
                    "Vélocité en baisse de " + dropPct.setScale(0, RoundingMode.HALF_UP)
                        + "% vs la semaine dernière"
                );
            }
        }

        return null;
    }

    private BigDecimal sumQuantityInRange(String articleId, List<Order> orders, Instant start, Instant endExclusive) {
        BigDecimal total = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order.getSaleDate() == null
                || order.getSaleDate().isBefore(start)
                || !order.getSaleDate().isBefore(endExclusive)) {
                continue;
            }
            total = total.add(quantityForArticle(order, articleId));
        }
        return total;
    }

    private BigDecimal quantityForArticle(Order order, String articleId) {
        if (order.getArticles() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.getArticles()) {
            if (articleId.equals(item.getArticleId())) {
                total = total.add(item.getQuantityOrdered());
            }
        }
        return total;
    }

    private ArticleDetailDTO.ArticlePeriodDTO buildPeriod(
        String key,
        String articleId,
        List<Order> allOrders,
        Instant now,
        ZoneId zone
    ) {
        Window window = resolveWindow(key, now, zone);

        BigDecimal totalCa = BigDecimal.ZERO;
        BigDecimal totalBenefice = BigDecimal.ZERO;
        BigDecimal quantitySold = BigDecimal.ZERO;
        int transactionsCount = 0;

        Map<String, BigDecimal> qtyBySeller = new LinkedHashMap<>();
        Map<String, BigDecimal> caBySeller = new LinkedHashMap<>();
        Map<String, String> sellerNames = new LinkedHashMap<>();

        // Nombre de commandes (contenant cet article) où chaque AUTRE article apparaît aussi.
        Map<String, Integer> coOccurrence = new HashMap<>();
        Map<String, String> otherNames = new HashMap<>();

        List<Order> articleOrdersInWindow = new ArrayList<>();

        for (Order order : allOrders) {
            if (order.getSaleDate() == null
                || order.getSaleDate().isBefore(window.start())
                || !order.getSaleDate().isBefore(window.end())
                || order.getArticles() == null) {
                continue;
            }

            boolean touchedThisOrder = false;
            for (OrderItem item : order.getArticles()) {
                if (!articleId.equals(item.getArticleId())) {
                    continue;
                }
                touchedThisOrder = true;
                BigDecimal lineCa = item.getPrice().multiply(item.getQuantityOrdered());
                totalCa = totalCa.add(lineCa);
                quantitySold = quantitySold.add(item.getQuantityOrdered());

                String email = order.getEmail() != null ? order.getEmail() : "";
                qtyBySeller.merge(email, item.getQuantityOrdered(), BigDecimal::add);
                caBySeller.merge(email, lineCa, BigDecimal::add);
                sellerNames.putIfAbsent(email, order.getSellerName());

                if (item.getUnitCost() != null) {
                    BigDecimal lineBenefice = item.getPrice()
                        .subtract(item.getUnitCost())
                        .multiply(item.getQuantityOrdered());
                    totalBenefice = totalBenefice.add(lineBenefice);
                }
            }

            if (touchedThisOrder) {
                transactionsCount++;
                articleOrdersInWindow.add(order);

                Set<String> otherArticlesSeen = new HashSet<>();
                for (OrderItem item : order.getArticles()) {
                    if (articleId.equals(item.getArticleId()) || !otherArticlesSeen.add(item.getArticleId())) {
                        continue;
                    }
                    coOccurrence.merge(item.getArticleId(), 1, Integer::sum);
                    otherNames.putIfAbsent(item.getArticleId(), item.getName());
                }
            }
        }

        long periodDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(window.start(), window.end()));
        BigDecimal velocityPerDay = quantitySold
            .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);

        BigDecimal marginRatePct = totalCa.compareTo(BigDecimal.ZERO) > 0
            ? totalBenefice.divide(totalCa, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        List<ArticleDetailDTO.SellerBreakdownDTO> bySeller = qtyBySeller.keySet().stream()
            .map(email -> new ArticleDetailDTO.SellerBreakdownDTO(
                email,
                sellerNames.getOrDefault(email, email),
                qtyBySeller.get(email),
                round(caBySeller.getOrDefault(email, BigDecimal.ZERO))
            ))
            .sorted((a, b) -> b.totalCa().compareTo(a.totalCa()))
            .toList();

        List<ArticleDetailDTO.AssociatedProductDTO> topAssociatedProducts = computeAssociatedProducts(
            window, coOccurrence, otherNames, articleOrdersInWindow.size()
        );

        return new ArticleDetailDTO.ArticlePeriodDTO(
            key,
            periodLabel(key),
            round(totalCa),
            round(totalBenefice),
            round(marginRatePct),
            quantitySold,
            transactionsCount,
            velocityPerDay,
            bySeller,
            topAssociatedProducts
        );
    }

    /**
     * Mesure le lien statistique entre cet article et chaque autre article qui apparaît
     * dans les mêmes commandes, via le lift : lift(A,B) = confidence(A→B) / support(B).
     * Un lift > 1 veut dire que B se vend plus souvent avec A qu'en moyenne — pas juste
     * "les deux se vendent bien", un vrai lien d'achat.
     */
    private List<ArticleDetailDTO.AssociatedProductDTO> computeAssociatedProducts(
        Window window,
        Map<String, Integer> coOccurrence,
        Map<String, String> otherNames,
        int articleOrderCount
    ) {
        if (articleOrderCount == 0 || coOccurrence.isEmpty()) {
            return List.of();
        }

        List<Order> allOrdersInWindow = orderRepository.findBySaleDateRange(window.start(), window.end());
        int totalOrders = allOrdersInWindow.size();
        if (totalOrders == 0) {
            return List.of();
        }

        Map<String, Integer> ordersContainingOther = new HashMap<>();
        for (Order order : allOrdersInWindow) {
            if (order.getArticles() == null) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            for (OrderItem item : order.getArticles()) {
                if (seen.add(item.getArticleId())) {
                    ordersContainingOther.merge(item.getArticleId(), 1, Integer::sum);
                }
            }
        }

        return coOccurrence.entrySet().stream()
            .filter(e -> e.getValue() >= MIN_CO_OCCURRENCE)
            .map(e -> {
                String otherId = e.getKey();
                int co = e.getValue();
                BigDecimal support = BigDecimal.valueOf(co)
                    .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP);
                BigDecimal confidence = BigDecimal.valueOf(co)
                    .divide(BigDecimal.valueOf(articleOrderCount), 4, RoundingMode.HALF_UP);
                int otherCount = ordersContainingOther.getOrDefault(otherId, 0);
                BigDecimal supportOther = otherCount > 0
                    ? BigDecimal.valueOf(otherCount).divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                BigDecimal lift = supportOther.compareTo(BigDecimal.ZERO) > 0
                    ? confidence.divide(supportOther, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                return new ArticleDetailDTO.AssociatedProductDTO(
                    otherId,
                    otherNames.get(otherId),
                    co,
                    round(support.multiply(BigDecimal.valueOf(100))),
                    round(confidence.multiply(BigDecimal.valueOf(100))),
                    round(lift)
                );
            })
            .filter(a -> a.lift().compareTo(BigDecimal.ONE) > 0)
            .sorted((a, b) -> b.lift().compareTo(a.lift()))
            .limit(TOP_ASSOCIATED_LIMIT)
            .toList();
    }

    private Window resolveWindow(String key, Instant now, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return switch (key) {
            case "7j" -> new Window(today.minusDays(6).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
            case "4sem" -> new Window(today.minusWeeks(4).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
            case "6mois" -> new Window(YearMonth.from(today).minusMonths(5).atDay(1).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
            case "2ans" -> new Window(LocalDate.of(today.getYear() - 1, 1, 1).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
            default -> throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "Période inconnue : " + key
            );
        };
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
