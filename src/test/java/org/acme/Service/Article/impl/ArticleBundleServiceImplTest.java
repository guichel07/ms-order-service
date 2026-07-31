package org.acme.Service.Article.impl;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.OccasionBundleRequestDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.OrderRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link ArticleBundleServiceImpl}.
 * OrderRepository et ArticleRepository sont mockés : aucune base de données n'est nécessaire.
 */
@ExtendWith(MockitoExtension.class)
class ArticleBundleServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleBundleServiceImpl articleBundleService;

    private final ZoneId zone = ZoneId.systemDefault();

    private Article article(String name, long price, long costPrice, int quantity) {
        Article a = new Article();
        a.id = new ObjectId();
        a.setName(name);
        a.setPrice(BigDecimal.valueOf(price));
        a.setCostPrice(BigDecimal.valueOf(costPrice));
        a.setQuantity(quantity);
        a.setArchived(false);
        return a;
    }

    private Order buildOrder(Instant saleDate, OrderItem... items) {
        Order order = new Order();
        order.id = new ObjectId();
        order.setSaleDate(saleDate);
        order.setArticles(new ArrayList<>(List.of(items)));
        return order;
    }

    private OrderItem item(Article article) {
        return new OrderItem(article.id.toHexString(), article.getName(), article.getPrice(), 1);
    }

    private Instant atHour(int daysAgo, int hour) {
        LocalDate day = LocalDate.now(zone).minusDays(daysAgo);
        return day.atStartOfDay(zone).plusHours(hour).toInstant();
    }

    private Instant atDayOfWeek(int weeksAgo, DayOfWeek dayOfWeek, int hour) {
        LocalDate day = LocalDate.now(zone).minusWeeks(weeksAgo).with(TemporalAdjusters.previousOrSame(dayOfWeek));
        return day.atStartOfDay(zone).plusHours(hour).toInstant();
    }

    private Instant atDayOfMonth(int monthsAgo, int dayOfMonth, int hour) {
        LocalDate baseMonth = LocalDate.now(zone).minusMonths(monthsAgo);
        LocalDate day = baseMonth.withDayOfMonth(Math.min(dayOfMonth, baseMonth.lengthOfMonth()));
        return day.atStartOfDay(zone).plusHours(hour).toInstant();
    }

    @Test
    void getBundlesForTimeOfDay_throwsBadRequest_whenPeriodKeyUnknown() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> articleBundleService.getBundlesForTimeOfDay("nuit-blanche")
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void getBundlesForTimeOfDay_returnsEmptyBundles_whenNoOrdersInWindow() {
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(List.of());
        when(articleRepository.listAll()).thenReturn(List.of());

        ArticleBundleDTO result = articleBundleService.getBundlesForTimeOfDay("matin");

        assertEquals("matin", result.periodKey());
        assertTrue(result.bundles().isEmpty());
    }

    @Test
    void getBundlesForTimeOfDay_ignoresOrders_outsideTheHourRange() {
        Article a = article("Café", 500, 200, 50);
        Article b = article("Pain", 300, 100, 50);

        // Toutes ces commandes sont l'après-midi (15h) - hors de la tranche "matin" (6h-11h).
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            orders.add(buildOrder(atHour(i, 15), item(a), item(b)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(a, b));

        ArticleBundleDTO result = articleBundleService.getBundlesForTimeOfDay("matin");

        assertTrue(result.bundles().isEmpty());
    }

    @Test
    void getBundlesForTimeOfDay_surfacesPair_onlyWhenLiftShowsARealLink_notJustPopularity() {
        Article a = article("Café", 500, 200, 50);
        Article b = article("Croissant", 300, 100, 50);
        Article c = article("Eau", 200, 80, 50);

        List<Order> orders = new ArrayList<>();
        // 3 commandes du matin A+B+C : B et C sont co-achetés avec A au même taux.
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 7), item(a), item(b), item(c)));
        }
        // 3 commandes du matin C seul : C est populaire indépendamment de A, B ne l'est pas.
        for (int i = 3; i < 6; i++) {
            orders.add(buildOrder(atHour(i, 8), item(c)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(a, b, c));

        ArticleBundleDTO result = articleBundleService.getBundlesForTimeOfDay("matin");

        // A+B : support 3/6=50%, support(A)=3/6=50%, support(B)=3/6=50% -> lift=2.0 (vrai lien).
        // A+C et B+C : support(C)=6/6=100% -> lift=1.0 (juste populaire, filtré).
        assertEquals(1, result.bundles().size());
        ArticleBundleDTO.BundleDTO bundle = result.bundles().get(0);
        assertEquals(3, bundle.coOccurrenceCount());
        assertEquals(0, new BigDecimal("2.0000").compareTo(bundle.lift()));
        assertTrue(
            (bundle.first().name().equals("Café") && bundle.second().name().equals("Croissant"))
                || (bundle.first().name().equals("Croissant") && bundle.second().name().equals("Café"))
        );
    }

    @Test
    void getBundlesForTimeOfDay_computesTotalPriceAsRawSum_andTotalMarginFromCostPrice() {
        Article a = article("Café", 500, 200, 50);
        Article b = article("Croissant", 300, 100, 50);
        Article c = article("Eau", 200, 80, 50);

        List<Order> orders = new ArrayList<>();
        // A+B+C ensemble 3 fois, C seul 3 fois -> A+B a un vrai lift (> 1), pas juste populaire.
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 7), item(a), item(b), item(c)));
        }
        for (int i = 3; i < 6; i++) {
            orders.add(buildOrder(atHour(i, 8), item(c)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(a, b, c));

        ArticleBundleDTO result = articleBundleService.getBundlesForTimeOfDay("matin");

        assertEquals(1, result.bundles().size());
        ArticleBundleDTO.BundleDTO bundle = result.bundles().get(0);
        // Prix : 500 + 300 = 800 (somme brute, pas de remise).
        assertEquals(0, new BigDecimal("800.00").compareTo(bundle.totalPrice()));
        // Marge : (500-200) + (300-100) = 500.
        assertEquals(0, new BigDecimal("500.00").compareTo(bundle.totalMargin()));
    }

    @Test
    void getBundlesForTimeOfDay_excludesArchivedAndOutOfStockArticles() {
        Article active = article("Café", 500, 200, 50);
        Article archived = article("Ancien café", 400, 150, 50);
        archived.setArchived(true);
        Article outOfStock = article("Sirop", 250, 100, 0);

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 7), item(active), item(archived), item(outOfStock)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(active, archived, outOfStock));

        ArticleBundleDTO result = articleBundleService.getBundlesForTimeOfDay("matin");

        assertTrue(result.bundles().isEmpty());
    }

    @Test
    void getBundlesForTimeOfDay_ranksBundles_byTotalMarginDescending() {
        Article lowMarginA = article("Bas de gamme A", 200, 180, 50);
        Article lowMarginB = article("Bas de gamme B", 200, 180, 50);
        Article highMarginA = article("Haut de gamme A", 1000, 300, 50);
        Article highMarginB = article("Haut de gamme B", 1000, 300, 50);

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 7), item(lowMarginA), item(lowMarginB)));
            orders.add(buildOrder(atHour(i, 8), item(highMarginA), item(highMarginB)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll())
            .thenReturn(List.of(lowMarginA, lowMarginB, highMarginA, highMarginB));

        ArticleBundleDTO result = articleBundleService.getBundlesForTimeOfDay("matin");

        assertEquals(2, result.bundles().size());
        // Le bundle le plus rentable (700+700=1400 de marge) doit être en tête, avant
        // celui à faible marge (20+20=40) — objectif "profit maximum".
        assertTrue(result.bundles().get(0).totalMargin().compareTo(result.bundles().get(1).totalMargin()) > 0);
        assertEquals(0, new BigDecimal("1400.00").compareTo(result.bundles().get(0).totalMargin()));
    }

    @Test
    void getBundlesForCalendarTrend_throwsBadRequest_whenTrendKeyUnknown() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> articleBundleService.getBundlesForCalendarTrend("milieu-de-mois")
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void getBundlesForCalendarTrend_debutSemaine_onlyKeepsLundiEtMardi() {
        Article a = article("Café", 500, 200, 50);
        Article b = article("Croissant", 300, 100, 50);
        Article c = article("Eau", 200, 80, 50);

        List<Order> orders = new ArrayList<>();
        // Lundi/mardi : A+B+C ensemble 3 fois -> doit être compté.
        for (int w = 1; w <= 3; w++) {
            orders.add(buildOrder(atDayOfWeek(w, DayOfWeek.MONDAY, 9), item(a), item(b), item(c)));
        }
        // Vendredi : mêmes articles, mais hors de la fenêtre "début de semaine" -> ignoré.
        for (int w = 1; w <= 3; w++) {
            orders.add(buildOrder(atDayOfWeek(w, DayOfWeek.FRIDAY, 9), item(a), item(b), item(c)));
        }
        // C seul le lundi/mardi, pour que le lift de A+B reste réel (pas juste C populaire).
        for (int w = 4; w <= 6; w++) {
            orders.add(buildOrder(atDayOfWeek(w, DayOfWeek.TUESDAY, 9), item(c)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(a, b, c));

        ArticleBundleDTO result = articleBundleService.getBundlesForCalendarTrend("debut-semaine");

        assertEquals("debut-semaine", result.periodKey());
        assertEquals(1, result.bundles().size());
        assertEquals(3, result.bundles().get(0).coOccurrenceCount());
    }

    @Test
    void getBundlesForCalendarTrend_finMois_onlyKeepsLastDaysOfMonth() {
        Article a = article("Riz", 1000, 400, 50);
        Article b = article("Huile", 800, 300, 50);
        Article c = article("Sel", 100, 40, 50);

        List<Order> orders = new ArrayList<>();
        // Fin de mois (jour >= 21) : A+B+C ensemble 3 fois -> doit être compté.
        for (int m = 1; m <= 3; m++) {
            orders.add(buildOrder(atDayOfMonth(m, 25, 10), item(a), item(b), item(c)));
        }
        // Début de mois : mêmes articles, hors de la fenêtre "fin de mois" -> ignoré.
        for (int m = 1; m <= 3; m++) {
            orders.add(buildOrder(atDayOfMonth(m, 3, 10), item(a), item(b), item(c)));
        }
        // C seul en fin de mois, pour que le lift de A+B reste réel.
        for (int m = 4; m <= 6; m++) {
            orders.add(buildOrder(atDayOfMonth(m, 22, 10), item(c)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(a, b, c));

        ArticleBundleDTO result = articleBundleService.getBundlesForCalendarTrend("fin-mois");

        assertEquals("fin-mois", result.periodKey());
        assertEquals(1, result.bundles().size());
        assertEquals(3, result.bundles().get(0).coOccurrenceCount());
    }

    @Test
    void getBundlesForCategory_throwsBadRequest_whenCategoryDoesNotExistInCatalog() {
        Article a = article("Café", 500, 200, 50);
        a.setCategory("Alimentaire");
        when(articleRepository.listAll()).thenReturn(List.of(a));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> articleBundleService.getBundlesForCategory("Règles")
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void getBundlesForCategory_onlyPairsArticles_withinTheRequestedCategory() {
        Article coton = article("Coton", 500, 200, 50);
        coton.setCategory("Hygiène");
        Article garniture = article("Garniture", 300, 100, 50);
        garniture.setCategory("Hygiène");
        Article riz = article("Riz", 1000, 400, 50);
        riz.setCategory("Alimentaire");

        List<Order> orders = new ArrayList<>();
        // Coton+Garniture (Hygiène) achetés ensemble 3 fois.
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 10), item(coton), item(garniture)));
        }
        // Riz (Alimentaire) seul, pour diluer le support global sans toucher Hygiène.
        for (int i = 3; i < 6; i++) {
            orders.add(buildOrder(atHour(i, 11), item(riz)));
        }
        // Coton souvent acheté avec Riz aussi, mais Riz est hors de la catégorie demandée -> jamais pairé.
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 12), item(coton), item(riz)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(coton, garniture, riz));

        ArticleBundleDTO result = articleBundleService.getBundlesForCategory("Hygiène");

        assertEquals(1, result.bundles().size());
        ArticleBundleDTO.BundleDTO bundle = result.bundles().get(0);
        assertTrue(
            (bundle.first().name().equals("Coton") && bundle.second().name().equals("Garniture"))
                || (bundle.first().name().equals("Garniture") && bundle.second().name().equals("Coton"))
        );
    }

    @Test
    void getBundlesForOccasion_throwsBadRequest_whenEndIsNotAfterStart() {
        Instant start = atHour(0, 20);
        Instant end = atHour(1, 20);

        OccasionBundleRequestDTO request = new OccasionBundleRequestDTO("Soirée LDC", start, end);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> articleBundleService.getBundlesForOccasion(request)
        );

        assertEquals(400, exception.getErrorCode().getStatusCode());
    }

    @Test
    void getBundlesForOccasion_usesTitleAsLabel_andComputesBundlesOnTheGivenWindow() {
        Article biere = article("Bière", 1500, 700, 50);
        Article chips = article("Chips", 800, 300, 50);
        Article eau = article("Eau", 200, 80, 50);

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 21), item(biere), item(chips), item(eau)));
        }
        for (int i = 3; i < 6; i++) {
            orders.add(buildOrder(atHour(i, 22), item(eau)));
        }

        Instant start = atHour(10, 0);
        Instant end = atHour(-1, 0);
        when(orderRepository.findBySaleDateRange(start, end)).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(biere, chips, eau));

        OccasionBundleRequestDTO request = new OccasionBundleRequestDTO("Soirée LDC", start, end);
        ArticleBundleDTO result = articleBundleService.getBundlesForOccasion(request);

        assertEquals("Soirée LDC", result.label());
        assertEquals(1, result.bundles().size());
        ArticleBundleDTO.BundleDTO bundle = result.bundles().get(0);
        assertTrue(
            (bundle.first().name().equals("Bière") && bundle.second().name().equals("Chips"))
                || (bundle.first().name().equals("Chips") && bundle.second().name().equals("Bière"))
        );
    }

    @Test
    void getCatalogSuggestions_surfacesPair_acrossTheWholeCatalog_noTimeOrCategoryFilter() {
        Article a = article("Café", 500, 200, 50);
        Article b = article("Croissant", 300, 100, 50);
        Article c = article("Eau", 200, 80, 50);

        List<Order> orders = new ArrayList<>();
        // A+B+C ensemble 3 fois, à des heures et jours différents (aucun filtre ici).
        for (int i = 0; i < 3; i++) {
            orders.add(buildOrder(atHour(i, 7), item(a), item(b), item(c)));
        }
        // C seul 3 fois : C est populaire indépendamment de A/B.
        for (int i = 3; i < 6; i++) {
            orders.add(buildOrder(atHour(i, 20), item(c)));
        }
        when(orderRepository.findBySaleDateRange(any(), any())).thenReturn(orders);
        when(articleRepository.listAll()).thenReturn(List.of(a, b, c));

        ArticleBundleDTO result = articleBundleService.getCatalogSuggestions();

        assertEquals("catalogue", result.periodKey());
        assertEquals(1, result.bundles().size());
        ArticleBundleDTO.BundleDTO bundle = result.bundles().get(0);
        assertTrue(
            (bundle.first().name().equals("Café") && bundle.second().name().equals("Croissant"))
                || (bundle.first().name().equals("Croissant") && bundle.second().name().equals("Café"))
        );
    }

}
