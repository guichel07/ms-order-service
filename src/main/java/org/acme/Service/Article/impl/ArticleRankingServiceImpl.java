package org.acme.Service.Article.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.acme.DTO.ArticleRankingEntryDTO;
import org.acme.Entity.Order;
import org.acme.Entity.OrderItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.OrderRepository;
import org.acme.Service.Article.ArticleRankingService;

/**
 * Classement catalogue pour une période — un seul passage sur les commandes de la
 * période, agrégées par article (même principe que StatsServiceImpl pour les
 * vendeurs). Le front en extrait best-sellers (haut) et flops (bas) en triant.
 */
@ApplicationScoped
public class ArticleRankingServiceImpl implements ArticleRankingService {

    private final OrderRepository orderRepository;

    public ArticleRankingServiceImpl(OrderRepository _orderRepository) {
        this.orderRepository = _orderRepository;
    }

    @Override
    public List<ArticleRankingEntryDTO> getRanking(String period) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant start = resolveWindowStart(period, today, zone);
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();

        List<Order> orders = orderRepository.findBySaleDateRange(start, end);

        Map<String, String> nameById = new LinkedHashMap<>();
        Map<String, BigDecimal> caById = new LinkedHashMap<>();
        Map<String, BigDecimal> beneficeById = new LinkedHashMap<>();
        Map<String, BigDecimal> quantityById = new LinkedHashMap<>();

        for (Order order : orders) {
            if (order.getArticles() == null) {
                continue;
            }
            for (OrderItem item : order.getArticles()) {
                String articleId = item.getArticleId();
                if (articleId == null) {
                    continue;
                }
                nameById.putIfAbsent(articleId, item.getName());

                BigDecimal lineCa = item.getPrice().multiply(item.getQuantityOrdered());
                caById.merge(articleId, lineCa, BigDecimal::add);
                quantityById.merge(articleId, item.getQuantityOrdered(), BigDecimal::add);

                if (item.getUnitCost() != null) {
                    BigDecimal lineBenefice = item.getPrice()
                        .subtract(item.getUnitCost())
                        .multiply(item.getQuantityOrdered());
                    beneficeById.merge(articleId, lineBenefice, BigDecimal::add);
                }
            }
        }

        List<ArticleRankingEntryDTO> ranking = new ArrayList<>();
        for (String articleId : nameById.keySet()) {
            ranking.add(new ArticleRankingEntryDTO(
                articleId,
                nameById.get(articleId),
                round(caById.getOrDefault(articleId, BigDecimal.ZERO)),
                round(beneficeById.getOrDefault(articleId, BigDecimal.ZERO)),
                quantityById.getOrDefault(articleId, BigDecimal.ZERO)
            ));
        }

        ranking.sort((a, b) -> b.totalCa().compareTo(a.totalCa()));
        return ranking;
    }

    private Instant resolveWindowStart(String period, LocalDate today, ZoneId zone) {
        return switch (period) {
            case "7j" -> today.minusDays(6).atStartOfDay(zone).toInstant();
            case "4sem" -> today.minusWeeks(4).atStartOfDay(zone).toInstant();
            case "6mois" -> YearMonth.from(today).minusMonths(5).atDay(1).atStartOfDay(zone).toInstant();
            case "2ans" -> LocalDate.of(today.getYear() - 1, 1, 1).atStartOfDay(zone).toInstant();
            default -> throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "Période inconnue : " + period
            );
        };
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
