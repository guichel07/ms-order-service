package org.acme.DTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrégat comportement + performance d'un vendeur (les 4 périodes 7j/4sem/6mois/2ans
 * en une seule réponse) — à consommer par le side panel vendeur côté front (voir
 * SellerDetail dans ms-section-stats/src/stats/index.ts). Ne porte aucune donnée
 * d'identité (nom/zone/téléphone) : ce service ne connaît que des emails sur les
 * commandes, l'identité du vendeur vient de ms-auth et est fusionnée côté front.
 */
public record SellerDetailDTO(
    String email,
    /** Null si rien à signaler (cadence normale, vente récente). */
    SellerAlertDTO alert,
    /** Heure de la première vente du dernier jour où le vendeur a vendu quelque chose. */
    String firstSaleTime,
    /** Heure de la dernière vente de ce même jour. */
    String lastSaleTime,
    /** Libellé de ce jour ("Aujourd'hui" ou une date) — null si aucune vente jamais. */
    String lastActiveDayLabel,
    List<SellerPeriodDTO> periods,
    String defaultPeriodKey
) {
    public record SellerAlertDTO(
        /** "warning" ou "critical". */
        String level,
        String message
    ) {}

    public record SellerPeriodDTO(
        String key,
        String label,
        BigDecimal totalCa,
        BigDecimal totalBenefice,
        BigDecimal averageBasket,
        int transactionsCount,
        BigDecimal itemsSoldCount,
        int activeBucketsCount,
        int totalBucketsCount,
        /** Un point par bucket de la période (jour/semaine/mois selon la clé) — sert à repérer les creux d'activité. */
        List<ActivityPointDTO> activity,
        List<HourlyPointDTO> hourlyPattern,
        List<TopItemDTO> topItems
    ) {}

    public record ActivityPointDTO(
        String label,
        BigDecimal ca,
        BigDecimal benefice,
        int salesCount,
        boolean active,
        /** Heure de la première vente de ce bucket précis — null si aucune vente dedans. */
        String firstSaleTime,
        /** Heure de la dernière vente de ce bucket précis — null si aucune vente dedans. */
        String lastSaleTime,
        /** Heures de ce bucket précis (drill-down) — vide si aucune vente dedans. */
        List<HourlyPointDTO> hourlyPattern
    ) {}

    public record HourlyPointDTO(int hour, int salesCount, BigDecimal ca) {}

    public record TopItemDTO(String name, String icon, BigDecimal quantity, BigDecimal ca) {}
}
