package org.acme.Service.Gamme.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.AiGeneratedContentDTO;
import org.acme.DTO.GammeItemRequestDTO;
import org.acme.DTO.GammeItemResponseDTO;
import org.acme.DTO.GammeRequestDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Gamme;
import org.acme.Entity.GammeItem;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.GammeRepository;
import org.acme.Service.Ai.AiContentClient;
import org.acme.Service.Gamme.GammeService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class GammeServiceImpl implements GammeService {

    private final GammeRepository gammeRepository;
    private final ArticleRepository articleRepository;
    private final AiContentClient aiContentClient;

    @Inject
    public GammeServiceImpl(
        GammeRepository _gammeRepository,
        ArticleRepository _articleRepository,
        AiContentClient _aiContentClient
    ) {
        this.gammeRepository = _gammeRepository;
        this.articleRepository = _articleRepository;
        this.aiContentClient = _aiContentClient;
    }

    @Override
    public GammeResponseDTO create(GammeRequestDTO request) {
        List<GammeItem> items = toGammeItems(request.items());

        Gamme gamme = new Gamme();
        gamme.setTitle(request.title());
        gamme.setItems(items);
        gamme.setCreatedAt(Instant.now());
        gamme.setUpdatedAt(gamme.getCreatedAt());
        gammeRepository.persist(gamme);

        return toResponseDTO(gamme);
    }

    @Override
    public GammeResponseDTO update(String id, GammeRequestDTO request) {
        Gamme gamme = findEntityById(id);
        List<GammeItem> items = toGammeItems(request.items());

        gamme.setTitle(request.title());
        gamme.setItems(items);
        gamme.setUpdatedAt(Instant.now());
        gammeRepository.update(gamme);

        return toResponseDTO(gamme);
    }

    @Override
    public List<GammeResponseDTO> listAll() {
        return gammeRepository.listAll().stream().map(this::toResponseDTO).toList();
    }

    @Override
    public GammeResponseDTO findById(String id) {
        return toResponseDTO(findEntityById(id));
    }

    @Override
    public GammeResponseDTO generateContent(String id) {
        Gamme gamme = findEntityById(id);
        GammeResponseDTO current = toResponseDTO(gamme);

        AiGeneratedContentDTO generated = aiContentClient.generateForGamme(current);

        gamme.setMarketingText(generated.marketingText());
        gamme.setPatternDescription(generated.usageTip());
        gamme.setContentGeneratedAt(Instant.now());
        gammeRepository.update(gamme);

        return toResponseDTO(gamme);
    }

    private Gamme findEntityById(String id) {
        Gamme gamme = gammeRepository.findById(new ObjectId(id));
        if (gamme == null) {
            throw new BusinessException(Response.Status.NOT_FOUND, "Gamme not found " + id);
        }
        return gamme;
    }

    /** Valide que chaque article existe encore et construit les lignes à persister (articleId/prix/quantité seulement). */
    private List<GammeItem> toGammeItems(List<GammeItemRequestDTO> requestItems) {
        return requestItems.stream()
            .map(item -> {
                if (articleRepository.findById(new ObjectId(item.articleId())) == null) {
                    throw new BusinessException(
                        Response.Status.BAD_REQUEST,
                        "Article inconnu : " + item.articleId()
                    );
                }
                return new GammeItem(item.articleId(), item.price(), item.quantity());
            })
            .toList();
    }

    /**
     * Enrichit chaque ligne avec les vraies données article actuelles (nom, icône, coût de revient,
     * stock) et calcule marge/total à partir du coût réel du moment — jamais celui de la création.
     */
    private GammeResponseDTO toResponseDTO(Gamme gamme) {
        List<GammeItemResponseDTO> items = gamme.getItems() == null
            ? List.of()
            : gamme.getItems().stream()
                .map(this::toItemResponseDTO)
                .filter(item -> item != null)
                .toList();

        BigDecimal totalPrice = items.stream()
            .map(GammeItemResponseDTO::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMargin = items.stream()
            .map(GammeItemResponseDTO::lineMargin)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new GammeResponseDTO(
            gamme.id.toHexString(),
            gamme.getTitle(),
            items,
            round(totalPrice),
            round(totalMargin),
            gamme.getMarketingText(),
            gamme.getPatternDescription(),
            gamme.getContentGeneratedAt(),
            gamme.getCreatedAt(),
            gamme.getUpdatedAt()
        );
    }

    private GammeItemResponseDTO toItemResponseDTO(GammeItem item) {
        Article article = articleRepository.findById(new ObjectId(item.getArticleId()));
        if (article == null) {
            // L'article a été supprimé de la base depuis — on ignore cette ligne plutôt que de planter.
            return null;
        }

        BigDecimal unitCost = article.getCostPrice() != null ? article.getCostPrice() : BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
        BigDecimal lineTotal = item.getPrice().multiply(quantity);
        BigDecimal lineMargin = item.getPrice().subtract(unitCost).multiply(quantity);

        return new GammeItemResponseDTO(
            item.getArticleId(),
            article.getName(),
            article.getIcon(),
            round(item.getPrice()),
            item.getQuantity(),
            round(unitCost),
            article.getQuantity(),
            round(lineTotal),
            round(lineMargin)
        );
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
