package org.acme.Service.Article;

import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.OccasionBundleRequestDTO;

public interface ArticleBundleService {
    /** periodKey attendu : "matin" (6h-11h), "midi" (11h-15h), "soir" (15h-20h). */
    ArticleBundleDTO getBundlesForTimeOfDay(String periodKey);

    /** trendKey attendu : "debut-semaine", "fin-semaine", "debut-mois", "fin-mois". */
    ArticleBundleDTO getBundlesForCalendarTrend(String trendKey);

    /** category : une des catégories réelles du catalogue (ex: "Hygiène", "Maison"). */
    ArticleBundleDTO getBundlesForCategory(String category);

    /** Thème manuel défini par l'admin (titre + fenêtre de dates), ex: "Soirée LDC". */
    ArticleBundleDTO getBundlesForOccasion(OccasionBundleRequestDTO request);

    /**
     * Paires les plus fortement liées sur tout le catalogue, toutes périodes confondues (fenêtre
     * de 6 mois, sans filtre horaire/catégorie) — sert de point de départ pour créer une gamme :
     * l'admin reconnaît un pattern humain dans un résultat déjà calculé plutôt que de partir d'une
     * liste vierge.
     */
    ArticleBundleDTO getCatalogSuggestions();
}
