package org.acme.Scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.Entity.Article;
import org.acme.Repository.ArticleRepository;
import org.acme.Service.Article.ArticleBundleService;
import org.acme.Service.BundleContent.BundleContentService;
import org.jboss.logging.Logger;

/**
 * Régénère chaque jour le contenu PDF (bundle + texte IA) pour toutes les tranches horaires,
 * tendances calendaires et catégories réelles du catalogue — les PDF servis à l'admin sont donc
 * déjà prêts (voir BundleContentService.getStored), pas recalculés à chaque affichage. Le thème
 * manuel/occasion n'est volontairement pas planifié ici : il est toujours à la demande
 * (voir ArticleResource.generateOccasionContent).
 */
@ApplicationScoped
public class BundleContentScheduler {

    private static final Logger LOG = Logger.getLogger(BundleContentScheduler.class);

    private static final List<String> TIME_OF_DAY_KEYS = List.of("matin", "midi", "soir");
    private static final List<String> CALENDAR_TREND_KEYS =
        List.of("debut-semaine", "fin-semaine", "debut-mois", "fin-mois");

    private final ArticleBundleService articleBundleService;
    private final BundleContentService bundleContentService;
    private final ArticleRepository articleRepository;

    @Inject
    public BundleContentScheduler(
        ArticleBundleService _articleBundleService,
        BundleContentService _bundleContentService,
        ArticleRepository _articleRepository
    ) {
        this.articleBundleService = _articleBundleService;
        this.bundleContentService = _bundleContentService;
        this.articleRepository = _articleRepository;
    }

    /** Tous les jours à 5h — avant l'ouverture, pour que le contenu du jour soit prêt à imprimer. */
    @Scheduled(cron = "0 0 5 * * ?")
    void regenerateDailyContent() {
        TIME_OF_DAY_KEYS.forEach(key ->
            tryGenerate(key, () -> articleBundleService.getBundlesForTimeOfDay(key))
        );
        CALENDAR_TREND_KEYS.forEach(key ->
            tryGenerate(key, () -> articleBundleService.getBundlesForCalendarTrend(key))
        );
        distinctActiveCategories().forEach(category ->
            tryGenerate(category, () -> articleBundleService.getBundlesForCategory(category))
        );
    }

    private void tryGenerate(String key, Supplier<ArticleBundleDTO> bundleSupplier) {
        try {
            bundleContentService.generateAndStore(bundleSupplier.get());
        } catch (Exception e) {
            // Une clé en échec (ex: catégorie supprimée entre-temps) ne doit pas bloquer les autres.
            LOG.errorf(e, "Échec de génération du contenu PDF pour la clé : %s", key);
        }
    }

    private Set<String> distinctActiveCategories() {
        return articleRepository.listAll().stream()
            .filter(a -> !a.isArchived())
            .map(Article::getCategory)
            .filter(category -> category != null && !category.isBlank())
            .collect(Collectors.toSet());
    }
}
