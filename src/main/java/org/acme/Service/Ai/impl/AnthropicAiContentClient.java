package org.acme.Service.Ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.ArticleBundleDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Service.Ai.AiContentClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Appelle Claude (Anthropic) pour rédiger le texte marketing — la sélection des produits reste un
 * calcul pur (lift) ou une composition déjà figée par l'admin (gamme) : l'IA ne fait jamais ce
 * choix, seulement le texte. Si aucune clé n'est configurée ou que l'appel échoue pour n'importe
 * quelle raison, se rabat silencieusement sur le texte gabarit de {@link StubAiContentClient}
 * plutôt que de faire échouer la requête admin.
 */
@ApplicationScoped
public class AnthropicAiContentClient implements AiContentClient {

    private static final Logger LOG = Logger.getLogger(AnthropicAiContentClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final Optional<String> apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final StubAiContentClient fallback = new StubAiContentClient();

    @Inject
    public AnthropicAiContentClient(
        @ConfigProperty(name = "anthropic.api.key") Optional<String> _apiKey,
        @ConfigProperty(name = "anthropic.model", defaultValue = "claude-sonnet-5") String _model,
        ObjectMapper _objectMapper
    ) {
        this.apiKey = _apiKey.filter(key -> !key.isBlank());
        this.model = _model;
        this.objectMapper = _objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public AiGeneratedContentDTO generate(ArticleBundleDTO bundle) {
        if (apiKey.isEmpty() || bundle.bundles().isEmpty()) {
            return fallback.generate(bundle);
        }

        ArticleBundleDTO.BundleDTO top = bundle.bundles().get(0);
        String prompt = String.format(
            """
            Tu es le rédacteur marketing de "Mama Solution", une boutique en République du Congo. \
            Réponds UNIQUEMENT avec un objet JSON strict, sans texte ni bloc de code autour, au format :
            {"marketingText":"...","usageTip":"..."}

            marketingText : une phrase courte et accrocheuse pour un client, mentionnant les deux \
            produits et le prix total.
            usageTip : un conseil d'usage contextualisé (habitudes locales) pour combiner ces deux \
            produits, ou null si non pertinent.

            Période : %s
            Produits : %s + %s
            Prix total : %s F
            """,
            bundle.label(), top.first().name(), top.second().name(), top.totalPrice().toPlainString()
        );

        return callClaude(prompt).orElseGet(() -> fallback.generate(bundle));
    }

    @Override
    public AiGeneratedContentDTO generateForGamme(GammeResponseDTO gamme) {
        if (apiKey.isEmpty() || gamme.items().isEmpty()) {
            return fallback.generateForGamme(gamme);
        }

        String itemsList = gamme.items().stream()
            .map(item -> (item.quantity() > 1 ? item.quantity() + "x " : "") + item.name())
            .collect(Collectors.joining(" + "));

        String prompt = String.format(
            """
            Tu es le rédacteur marketing de "Mama Solution", une boutique en République du Congo. \
            Un admin a déjà composé cette gamme (articles, prix, quantités figés) — ne propose \
            jamais de changement dessus, rédige seulement le texte. \
            Réponds UNIQUEMENT avec un objet JSON strict, sans texte ni bloc de code autour, au format :
            {"marketingText":"...","patternDescription":"..."}

            marketingText : une phrase courte et accrocheuse pour le client, mentionnant la \
            composition et le prix total.
            patternDescription : le pattern humain que tu reconnais dans cette composition (ex: \
            "petit-déjeuner congolais"), une phrase courte, à usage interne pour l'admin.

            Titre : %s
            Composition : %s
            Prix total : %s F
            """,
            gamme.title(), itemsList, gamme.totalPrice().toPlainString()
        );

        return callClaude(prompt).orElseGet(() -> fallback.generateForGamme(gamme));
    }

    private Optional<AiGeneratedContentDTO> callClaude(String prompt) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", 512,
                "messages", List.of(Map.of("role", "user", "content", prompt))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(20))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey.orElseThrow())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn("Appel Claude échoué (HTTP " + response.statusCode() + ") — repli sur le texte gabarit.");
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = extractTextBlock(root.path("content"));
            if (text == null) {
                LOG.warn("Réponse Claude vide — repli sur le texte gabarit.");
                return Optional.empty();
            }

            return parseGeneratedContent(text);
        } catch (Exception e) {
            LOG.warn("Erreur lors de l'appel à Claude — repli sur le texte gabarit.", e);
            return Optional.empty();
        }
    }

    /**
     * Le "extended thinking" ajoute un bloc {"type":"thinking"} avant le texte — content[0] n'est
     * donc pas fiable, il faut chercher le premier bloc de type "text".
     */
    private String extractTextBlock(JsonNode content) {
        if (!content.isArray()) {
            return null;
        }
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText(null);
            }
        }
        return null;
    }

    private Optional<AiGeneratedContentDTO> parseGeneratedContent(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            return Optional.empty();
        }

        try {
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            String marketingText = node.path("marketingText").asText(null);
            if (marketingText == null) {
                return Optional.empty();
            }
            String secondary = node.has("usageTip")
                ? node.path("usageTip").asText(null)
                : node.path("patternDescription").asText(null);
            return Optional.of(new AiGeneratedContentDTO(marketingText, secondary));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
