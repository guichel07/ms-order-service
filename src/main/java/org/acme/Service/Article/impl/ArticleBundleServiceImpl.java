package org.acme.Service.Article.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.OccasionBundleRequestDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Article.ArticleBundleService;

/**
 * Bundles "produits qui vont ensemble" par tranche horaire, pour les PDF marketing.
 * Même statistique de lift que ArticleDetailServiceImpl, mais appliquée à tout le
 * catalogue (paires d'articles) au lieu d'un seul article, et scopée sur l'heure de la
 * commande plutôt que sur sa date.
 */
@ApplicationScoped
public class ArticleBundleServiceImpl implements ArticleBundleService {

    /** En dessous, une co-occurrence est probablement une coïncidence. */
    private static final int MIN_CO_OCCURRENCE = 2;
    private static final int TOP_BUNDLES_LIMIT = 5;
    /** Fenêtre glissante de données — assez large pour la fiabilité statistique, assez récente pour rester pertinente. */
    private static final int DATA_WINDOW_MONTHS = 6;

    private final OrderRepository orderRepository;
    private final ArticleRepository articleRepository;

    public ArticleBundleServiceImpl(OrderRepository _orderRepository, ArticleRepository _articleRepository) {
        this.orderRepository = _orderRepository;
        this.articleRepository = _articleRepository;
    }

    private record HourRange(int startHour, int endHourExclusive) {}

    @Override
    public ArticleBundleDTO getBundlesForTimeOfDay(String periodKey) {
        HourRange range = resolveHourRange(periodKey);
        ZoneId zone = ZoneId.systemDefault();

        List<Order> ordersInSlot = ordersInDataWindow(zone).stream()
            .filter(o -> o.getSaleDate() != null && isInHourRange(o.getSaleDate(), zone, range))
            .toList();

        return computeBundleDTO(periodKey, periodLabel(periodKey), ordersInSlot);
    }

    @Override
    public ArticleBundleDTO getBundlesForCalendarTrend(String trendKey) {
        ZoneId zone = ZoneId.systemDefault();
        validateTrendKey(trendKey);

        List<Order> ordersInSlot = ordersInDataWindow(zone).stream()
            .filter(o -> o.getSaleDate() != null && isInCalendarTrend(o.getSaleDate(), zone, trendKey))
            .toList();

        return computeBundleDTO(trendKey, trendLabel(trendKey), ordersInSlot);
    }

    @Override
    public ArticleBundleDTO getBundlesForCategory(String category) {
        List<Article> allArticles = articleRepository.listAll();
        boolean categoryExists = allArticles.stream().anyMatch(a -> category.equals(a.getCategory()));
        if (!categoryExists) {
            throw new BusinessException(Response.Status.BAD_REQUEST, "Catégorie inconnue : " + category);
        }

        ZoneId zone = ZoneId.systemDefault();
        List<Order> ordersInWindow = ordersInDataWindow(zone);

        return computeBundleDTO("categorie:" + category, "Besoin : " + category, ordersInWindow, category);
    }

    @Override
    public ArticleBundleDTO getBundlesForOccasion(OccasionBundleRequestDTO request) {
        if (!request.end().isAfter(request.start())) {
            throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "La date de fin doit être après la date de début"
            );
        }

        List<Order> ordersInWindow = orderRepository.findBySaleDateRange(request.start(), request.end());

        return computeBundleDTO("occasion:" + request.title(), request.title(), ordersInWindow);
    }

    @Override
    public ArticleBundleDTO getCatalogSuggestions() {
        ZoneId zone = ZoneId.systemDefault();
        return computeBundleDTO("catalogue", "Suggestions catalogue", ordersInDataWindow(zone));
    }

    /** Fenêtre glissante de commandes sur laquelle tous les moteurs de bundle piochent, avant leur propre filtre temporel. */
    private List<Order> ordersInDataWindow(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        Instant dataWindowStart = today.minusMonths(DATA_WINDOW_MONTHS).atStartOfDay(zone).toInstant();
        Instant dataWindowEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        return orderRepository.findBySaleDateRange(dataWindowStart, dataWindowEnd);
    }

    private ArticleBundleDTO computeBundleDTO(String key, String label, List<Order> ordersInSlot) {
        return computeBundleDTO(key, label, ordersInSlot, null);
    }

    private ArticleBundleDTO computeBundleDTO(String key, String label, List<Order> ordersInSlot, String categoryFilter) {
        Map<String, Article> articlesById = new HashMap<>();
        for (Article article : articleRepository.listAll()) {
            boolean matchesCategory = categoryFilter == null || categoryFilter.equals(article.getCategory());
            if (!article.isArchived() && article.getQuantity().compareTo(BigDecimal.ZERO) > 0 && matchesCategory) {
                articlesById.put(article.id.toHexString(), article);
            }
        }

        Map<String, Integer> ordersContaining = new HashMap<>();
        Map<String, Integer> pairCoOccurrence = new HashMap<>();

        for (Order order : ordersInSlot) {
            if (order.getArticles() == null) {
                continue;
            }
            Set<String> distinctIds = new HashSet<>();
            for (OrderItem item : order.getArticles()) {
                if (articlesById.containsKey(item.getArticleId())) {
                    distinctIds.add(item.getArticleId());
                }
            }
            for (String id : distinctIds) {
                ordersContaining.merge(id, 1, Integer::sum);
            }

            List<String> sortedIds = distinctIds.stream().sorted().toList();
            for (int i = 0; i < sortedIds.size(); i++) {
                for (int j = i + 1; j < sortedIds.size(); j++) {
                    String pairKey = sortedIds.get(i) + "|" + sortedIds.get(j);
                    pairCoOccurrence.merge(pairKey, 1, Integer::sum);
                }
            }
        }

        int totalOrders = ordersInSlot.size();
        List<ArticleBundleDTO.BundleDTO> bundles = totalOrders == 0
            ? List.of()
            : buildBundles(pairCoOccurrence, ordersContaining, articlesById, totalOrders);

        return new ArticleBundleDTO(key, label, bundles);
    }

    private List<ArticleBundleDTO.BundleDTO> buildBundles(
        Map<String, Integer> pairCoOccurrence,
        Map<String, Integer> ordersContaining,
        Map<String, Article> articlesById,
        int totalOrders
    ) {
        return pairCoOccurrence.entrySet().stream()
            .filter(e -> e.getValue() >= MIN_CO_OCCURRENCE)
            .map(e -> toBundle(e.getKey(), e.getValue(), ordersContaining, articlesById, totalOrders))
            .filter(b -> b != null && b.lift().compareTo(BigDecimal.ONE) > 0)
            // Objectif "profit maximum" : parmi les paires statistiquement valides (lift > 1),
            // on met en avant celles qui rapportent le plus, pas juste les plus fortement liées.
            .sorted((a, b) -> b.totalMargin().compareTo(a.totalMargin()))
            .limit(TOP_BUNDLES_LIMIT)
            .toList();
    }

    private ArticleBundleDTO.BundleDTO toBundle(
        String pairKey,
        int coOccurrence,
        Map<String, Integer> ordersContaining,
        Map<String, Article> articlesById,
        int totalOrders
    ) {
        String[] ids = pairKey.split("\\|");
        Article first = articlesById.get(ids[0]);
        Article second = articlesById.get(ids[1]);
        if (first == null || second == null) {
            return null;
        }

        int firstOrders = ordersContaining.getOrDefault(ids[0], 0);
        int secondOrders = ordersContaining.getOrDefault(ids[1], 0);
        if (firstOrders == 0 || secondOrders == 0) {
            return null;
        }

        BigDecimal support = BigDecimal.valueOf(coOccurrence)
            .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP);
        BigDecimal supportFirst = BigDecimal.valueOf(firstOrders)
            .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP);
        BigDecimal supportSecond = BigDecimal.valueOf(secondOrders)
            .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP);
        BigDecimal confidence = BigDecimal.valueOf(coOccurrence)
            .divide(BigDecimal.valueOf(firstOrders), 4, RoundingMode.HALF_UP);

        BigDecimal denominator = supportFirst.multiply(supportSecond);
        BigDecimal lift = denominator.compareTo(BigDecimal.ZERO) > 0
            ? support.divide(denominator, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal totalPrice = first.getPrice().add(second.getPrice());
        BigDecimal totalMargin = marginOf(first).add(marginOf(second));

        return new ArticleBundleDTO.BundleDTO(
            toItem(first),
            toItem(second),
            coOccurrence,
            round(support.multiply(BigDecimal.valueOf(100))),
            round(confidence.multiply(BigDecimal.valueOf(100))),
            round(lift),
            round(totalPrice),
            round(totalMargin)
        );
    }

    private BigDecimal marginOf(Article article) {
        return article.getCostPrice() != null
            ? article.getPrice().subtract(article.getCostPrice())
            : BigDecimal.ZERO;
    }

    private ArticleBundleDTO.BundleItemDTO toItem(Article article) {
        return new ArticleBundleDTO.BundleItemDTO(
            article.id.toHexString(),
            article.getName(),
            round(article.getPrice()),
            article.getQuantity(),
            article.getExpirationDate()
        );
    }

    private boolean isInHourRange(Instant saleDate, ZoneId zone, HourRange range) {
        int hour = ZonedDateTime.ofInstant(saleDate, zone).getHour();
        return hour >= range.startHour() && hour < range.endHourExclusive();
    }

    private HourRange resolveHourRange(String periodKey) {
        return switch (periodKey) {
            case "matin" -> new HourRange(6, 11);
            case "midi" -> new HourRange(11, 15);
            case "soir" -> new HourRange(15, 20);
            default -> throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "Tranche horaire inconnue : " + periodKey
            );
        };
    }

    private String periodLabel(String periodKey) {
        return switch (periodKey) {
            case "matin" -> "Matin (6h-11h)";
            case "midi" -> "Midi (11h-15h)";
            case "soir" -> "Soir (15h-20h)";
            default -> periodKey;
        };
    }

    private static final Set<DayOfWeek> START_OF_WEEK_DAYS = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
    private static final Set<DayOfWeek> END_OF_WEEK_DAYS = EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    /** Au-delà, on considère qu'on est en fin de mois, quelle que soit la longueur réelle du mois. */
    private static final int END_OF_MONTH_DAY_THRESHOLD = 21;
    private static final int START_OF_MONTH_LAST_DAY = 10;

    private boolean isInCalendarTrend(Instant saleDate, ZoneId zone, String trendKey) {
        ZonedDateTime date = ZonedDateTime.ofInstant(saleDate, zone);
        return switch (trendKey) {
            case "debut-semaine" -> START_OF_WEEK_DAYS.contains(date.getDayOfWeek());
            case "fin-semaine" -> END_OF_WEEK_DAYS.contains(date.getDayOfWeek());
            case "debut-mois" -> date.getDayOfMonth() <= START_OF_MONTH_LAST_DAY;
            case "fin-mois" -> date.getDayOfMonth() >= END_OF_MONTH_DAY_THRESHOLD;
            default -> throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "Tendance calendaire inconnue : " + trendKey
            );
        };
    }

    private void validateTrendKey(String trendKey) {
        if (!Set.of("debut-semaine", "fin-semaine", "debut-mois", "fin-mois").contains(trendKey)) {
            throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "Tendance calendaire inconnue : " + trendKey
            );
        }
    }

    private String trendLabel(String trendKey) {
        return switch (trendKey) {
            case "debut-semaine" -> "Début de semaine (lundi-mardi)";
            case "fin-semaine" -> "Fin de semaine (vendredi-dimanche)";
            case "debut-mois" -> "Début de mois (1-10)";
            case "fin-mois" -> "Fin de mois (21-fin)";
            default -> trendKey;
        };
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
