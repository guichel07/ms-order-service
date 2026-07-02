package org.acme.DTO;

import java.math.BigDecimal;
import java.util.List;
import org.acme.Entity.AgeCategory;
import org.acme.Entity.Gender;

/**
 * Agrégat complet de la fiche client — les 4 périodes (7j/4sem/6mois/2ans) en une
 * seule réponse, à injecter tel quel côté front — voir ClientDetail / ClientPeriodData
 * dans ms-section-clients/src/clients/index.ts.
 */
public record ClientDetailDTO(
    String id,
    String firstname,
    String lastname,
    String phone,
    AgeCategory ageCategory,
    Gender gender,
    boolean anonymous,
    String segment,
    boolean isAtRisk,
    Integer daysSinceLastOrder,
    String clientSinceLabel,
    List<PeriodDTO> periods,
    String defaultPeriodKey
) {
    public record PeriodDTO(
        String key,
        String label,
        BigDecimal totalCa,
        BigDecimal totalBenefice,
        BigDecimal averageOrderValue,
        int ordersCount,
        String purchaseRhythmLabel,
        List<MonthlyPointDTO> monthlyTrend,
        List<HourlyPointDTO> hourlyPattern,
        List<TopProductDTO> topProducts,
        List<OrderSummaryDTO> orders
    ) {}

    public record MonthlyPointDTO(String label, BigDecimal ca, BigDecimal benefice) {}

    public record HourlyPointDTO(int hour, int ordersCount, BigDecimal ca) {}

    public record TopProductDTO(String name, String icon, int quantity, BigDecimal ca) {}

    public record OrderSummaryDTO(
        String date,
        String items,
        String extraItems,
        BigDecimal amount,
        BigDecimal margin
    ) {}
}
