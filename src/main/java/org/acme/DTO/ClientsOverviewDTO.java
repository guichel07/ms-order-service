package org.acme.DTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agrégat complet de l'onglet "Analyse" (vue globale clients) — mois courant,
 * en une seule réponse. Miroir de ClientsOverviewData côté front
 * (ms-section-clients/src/clients/overview.ts).
 */
public record ClientsOverviewDTO(
    List<HourlyTrafficPointDTO> hourlyTraffic,
    List<DayOfMonthPointDTO> dayOfMonthTrend,
    NewVsReturningSplitDTO newVsReturning,
    List<TopClientDTO> topClients,
    List<TopProductDTO> topVipProducts,
    List<AtRiskClientDTO> atRiskClients,
    List<TimeSlotSummaryDTO> timeSlots,
    List<ProductComboDTO> frequentCombos
) {
    public record HourlyTrafficPointDTO(int hour, int ordersCount, BigDecimal ca) {}

    public record DayOfMonthPointDTO(int day, BigDecimal ca, List<HourlyTrafficPointDTO> hourlyTraffic) {}

    public record NewVsReturningSplitDTO(
        BigDecimal newClientsCa,
        BigDecimal returningClientsCa,
        int newClientsCount,
        int returningClientsCount
    ) {}

    public record TopClientDTO(
        String id,
        String firstname,
        String lastname,
        BigDecimal totalSpent,
        BigDecimal averageOrderValue,
        String segment
    ) {}

    public record TopProductDTO(String name, String icon, int quantity, BigDecimal ca) {}

    public record AtRiskClientDTO(
        String id,
        String firstname,
        String lastname,
        String segment,
        int daysSinceLastOrder
    ) {}

    public record TimeSlotSummaryDTO(
        String key,
        String label,
        BigDecimal averageOrderValue,
        List<TopProductDTO> topProducts
    ) {}

    public record ProductComboDTO(List<String> products, List<String> icons, int count) {}
}
