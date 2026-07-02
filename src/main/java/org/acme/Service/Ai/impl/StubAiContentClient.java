package org.acme.Service.Ai.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Service.Ai.AiContentClient;

/**
 * Génère un texte gabarit à partir des données du bundle, sans appel réseau — utilisé comme repli
 * par {@link AnthropicAiContentClient} quand aucune clé API n'est configurée ou que l'appel à
 * Claude échoue. Pas un bean CDI (instancié directement) pour éviter toute ambiguïté avec
 * AnthropicAiContentClient, seule implémentation injectable de AiContentClient.
 */
public class StubAiContentClient implements AiContentClient {

    @Override
    public AiGeneratedContentDTO generate(ArticleBundleDTO bundle) {
        if (bundle.bundles().isEmpty()) {
            return new AiGeneratedContentDTO(
                "Pas assez de données pour proposer une offre sur \"" + bundle.label() + "\" pour le moment.",
                null
            );
        }

        ArticleBundleDTO.BundleDTO top = bundle.bundles().get(0);
        String marketingText = String.format(
            "%s : %s + %s à %s F.",
            bundle.label(),
            top.first().name(),
            top.second().name(),
            top.totalPrice().toPlainString()
        );
        String usageTip = String.format(
            "%s et %s se marient bien ensemble — à combiner selon vos habitudes.",
            top.first().name(),
            top.second().name()
        );

        return new AiGeneratedContentDTO(marketingText, usageTip);
    }

    @Override
    public AiGeneratedContentDTO generateForGamme(GammeResponseDTO gamme) {
        if (gamme.items().isEmpty()) {
            return new AiGeneratedContentDTO(
                "Ajoute au moins un article pour générer le texte de \"" + gamme.title() + "\".",
                null
            );
        }

        String itemsList = gamme.items().stream()
            .map(item -> (item.quantity() > 1 ? item.quantity() + "x " : "") + item.name())
            .collect(Collectors.joining(" + "));

        String marketingText = String.format(
            "%s : %s — %s F le menu.",
            gamme.title(),
            itemsList,
            gamme.totalPrice().toPlainString()
        );

        BigDecimal marginRatePct = gamme.totalPrice().compareTo(BigDecimal.ZERO) > 0
            ? gamme.totalMargin()
                .divide(gamme.totalPrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String patternDescription = String.format(
            "Composition reconnue pour \"%s\" — %d produit%s, %s%% de marge.",
            gamme.title(),
            gamme.items().size(),
            gamme.items().size() > 1 ? "s" : "",
            marginRatePct.toPlainString()
        );

        return new AiGeneratedContentDTO(marketingText, patternDescription);
    }
}
