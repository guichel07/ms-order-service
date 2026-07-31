package org.acme.Scheduler;

import java.math.BigDecimal;
import java.util.List;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.Entity.Article;
import org.acme.Repository.ArticleRepository;
import org.acme.Service.Article.ArticleBundleService;
import org.acme.Service.BundleContent.BundleContentService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link BundleContentScheduler}.
 * Toutes les dépendances sont mockées — aucune base de données ni vrai scheduler Quarkus
 * nécessaire, on appelle directement la méthode planifiée.
 */
@ExtendWith(MockitoExtension.class)
class BundleContentSchedulerTest {

    @Mock
    private ArticleBundleService articleBundleService;

    @Mock
    private BundleContentService bundleContentService;

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private BundleContentScheduler scheduler;

    private Article article(String category, boolean archived) {
        Article a = new Article();
        a.id = new ObjectId();
        a.setCategory(category);
        a.setArchived(archived);
        a.setPrice(BigDecimal.TEN);
        return a;
    }

    private ArticleBundleDTO emptyBundle(String key) {
        return new ArticleBundleDTO(key, key, List.of());
    }

    @Test
    void regenerateDailyContent_generatesContent_forEveryTimeOfDayAndCalendarTrendKey() {
        when(articleRepository.listAll()).thenReturn(List.of());
        when(articleBundleService.getBundlesForTimeOfDay(any())).thenAnswer(i -> emptyBundle(i.getArgument(0)));
        when(articleBundleService.getBundlesForCalendarTrend(any())).thenAnswer(i -> emptyBundle(i.getArgument(0)));

        scheduler.regenerateDailyContent();

        for (String key : List.of("matin", "midi", "soir")) {
            verify(articleBundleService).getBundlesForTimeOfDay(key);
        }
        for (String key : List.of("debut-semaine", "fin-semaine", "debut-mois", "fin-mois")) {
            verify(articleBundleService).getBundlesForCalendarTrend(key);
        }
        verify(bundleContentService, times(7)).generateAndStore(any());
    }

    @Test
    void regenerateDailyContent_generatesContent_forEachDistinctActiveCategory_notArchivedOnes() {
        when(articleRepository.listAll()).thenReturn(List.of(
            article("Hygiène", false),
            article("Hygiène", false),
            article("Maison", false),
            article("Alimentaire", true)
        ));
        when(articleBundleService.getBundlesForTimeOfDay(any())).thenAnswer(i -> emptyBundle(i.getArgument(0)));
        when(articleBundleService.getBundlesForCalendarTrend(any())).thenAnswer(i -> emptyBundle(i.getArgument(0)));
        when(articleBundleService.getBundlesForCategory(any())).thenAnswer(i -> emptyBundle(i.getArgument(0)));

        scheduler.regenerateDailyContent();

        verify(articleBundleService).getBundlesForCategory("Hygiène");
        verify(articleBundleService).getBundlesForCategory("Maison");
        verify(articleBundleService, never()).getBundlesForCategory("Alimentaire");
    }

    @Test
    void regenerateDailyContent_continuesOtherKeys_whenOneKeyFails() {
        when(articleRepository.listAll()).thenReturn(List.of());
        when(articleBundleService.getBundlesForTimeOfDay(eq("matin"))).thenThrow(new RuntimeException("boom"));
        when(articleBundleService.getBundlesForTimeOfDay(eq("midi"))).thenAnswer(i -> emptyBundle("midi"));
        when(articleBundleService.getBundlesForTimeOfDay(eq("soir"))).thenAnswer(i -> emptyBundle("soir"));
        when(articleBundleService.getBundlesForCalendarTrend(any())).thenAnswer(i -> emptyBundle(i.getArgument(0)));

        scheduler.regenerateDailyContent();

        verify(bundleContentService, times(6)).generateAndStore(any());
    }
}
