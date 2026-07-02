package org.acme.DTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Réponse de l'agrégation de stats vendeurs pour une période donnée — voir
 * ms-section-stats/src/stats/index.ts (Metrics, PeriodDelta, TrendBucket, StatsPeriod)
 * pour le contrat consommé côté front. Le front possède déjà la clé/le libellé
 * de la période elle-même ; seules les données sont renvoyées ici.
 */
public record StatsResponseDTO(
    Map<String, MetricsDTO> metrics,

    /** null si la période précédente n'a aucune vente (évite une division par zéro). */
    PeriodDeltaDTO delta,

    List<TrendBucketDTO> trend,

    /** Heure de la première vente (toute équipe confondue) du dernier jour où quelqu'un a vendu — null si aucune vente. */
    String firstSaleTime,
    /** Heure de la dernière vente de ce même jour. */
    String lastSaleTime,
    /** Affluence heure par heure (toute équipe), agrégée sur toute la période — vue par défaut quand aucun bucket n'est sélectionné. */
    List<HourlyPointDTO> hourlyPattern
) {
    public record MetricsDTO(BigDecimal ca, BigDecimal benefice) {}

    public record PeriodDeltaDTO(BigDecimal pct, boolean positive, String label) {}

    public record HourlyPointDTO(int hour, int salesCount, BigDecimal ca) {}

    public record TrendBucketDTO(
        String key,
        String label,
        BigDecimal ca,
        BigDecimal benefice,
        Map<String, MetricsDTO> bySeller,
        /** Heure de la première vente de ce bucket précis (drill-down) — null si aucune vente dedans. */
        String firstSaleTime,
        String lastSaleTime,
        /** Heures de ce bucket précis (drill-down) — vide si aucune vente dedans. */
        List<HourlyPointDTO> hourlyPattern
    ) {}
}
