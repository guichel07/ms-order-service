package org.acme.Service.Article.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.acme.DTO.ArticleRequestDTO;
import org.acme.DTO.ArticleResponseDTO;
import org.acme.Entity.Article;
import org.acme.Entity.PriceReview;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Service.Article.ArticleService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class ArticleServiceImpl implements ArticleService {

    /** Même barème que PRICING_MARGINS.cible côté front (ms-section-admin/src/constants). */
    private static final BigDecimal TARGET_MARGIN = BigDecimal.valueOf(1.4);
    /** Même seuil que PRICE_REVIEW_THRESHOLD côté front. */
    private static final BigDecimal PRICE_REVIEW_THRESHOLD = BigDecimal.valueOf(0.05);

    private final ArticleRepository articleRepository;

    public ArticleServiceImpl(ArticleRepository _articleRepository) {
        this.articleRepository = _articleRepository;
    }

    /** Coût de revient à l'unité — toujours recalculé ici, jamais reçu tel quel du front. */
    private BigDecimal computeCostPrice(ArticleRequestDTO articleDTO) {
        BigDecimal purchasePrice = articleDTO.purchasePrice() != null ? articleDTO.purchasePrice() : BigDecimal.ZERO;
        BigDecimal transport = articleDTO.transport() != null ? articleDTO.transport() : BigDecimal.ZERO;
        BigDecimal misc = articleDTO.misc() != null ? articleDTO.misc() : BigDecimal.ZERO;
        BigDecimal total = purchasePrice.add(transport).add(misc);

        if (articleDTO.purchasedQuantity() <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(articleDTO.purchasedQuantity()), 4, RoundingMode.HALF_UP);
    }

    /**
     * Ne signale que si l'article était déjà verrouillé (locked) et le reste sur cette
     * mise à jour — un article non verrouillé est censé suivre son coût automatiquement,
     * pas besoin d'un bandeau — et seulement si le coût a bougé de plus du seuil.
     */
    private PriceReview evaluatePriceReview(
        Article existing,
        ArticleRequestDTO articleDTO,
        BigDecimal newCostPrice
    ) {
        if (!existing.isLocked() || !articleDTO.locked()) {
            return null;
        }

        BigDecimal previousCost = existing.getCostPrice();
        if (previousCost == null || previousCost.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal change = newCostPrice.subtract(previousCost).abs().divide(previousCost, 4, RoundingMode.HALF_UP);
        if (change.compareTo(PRICE_REVIEW_THRESHOLD) < 0) {
            return null;
        }

        BigDecimal recommendedPrice = newCostPrice.multiply(TARGET_MARGIN).setScale(0, RoundingMode.HALF_UP);
        BigDecimal marginDrop = newCostPrice.subtract(previousCost).max(BigDecimal.ZERO);

        return new PriceReview(existing.getPrice(), newCostPrice, recommendedPrice, marginDrop);
    }

    public List<ArticleResponseDTO> listAll() {
        return articleRepository
            .listAll()
            .stream()
            .map(ArticleResponseDTO::fromEntity)
            .toList();
    }

    public ArticleResponseDTO findByName(String name) {
        Article articleFound = articleRepository.findByName(name);

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found"
            );
        }

        return ArticleResponseDTO.fromEntity(articleFound);
    }

    public ArticleResponseDTO findById(String id) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        return ArticleResponseDTO.fromEntity(articleFound);
    }

    public ArticleResponseDTO register(ArticleRequestDTO articleDTO) {
        if (articleRepository.count("name", articleDTO.name()) != 0) {
            throw new BusinessException(
                Response.Status.CONFLICT,
                "Un article avec ce nom existe déjà"
            );
        }

        Article article = new Article();
        article.setName(articleDTO.name());
        article.setIcon(articleDTO.icon());
        article.setColor(articleDTO.color());
        article.setCategory(articleDTO.category());
        article.setPrice(articleDTO.price());
        article.setMaxPrice(articleDTO.maxPrice());
        article.setMinPrice(articleDTO.minPrice());
        article.setCostPrice(computeCostPrice(articleDTO));
        article.setMarketPrice(articleDTO.marketPrice());
        article.setQuantity(articleDTO.quantity());
        article.setPurchasePrice(articleDTO.purchasePrice());
        article.setTransport(articleDTO.transport());
        article.setMisc(articleDTO.misc());
        article.setPurchasedQuantity(articleDTO.purchasedQuantity());
        article.setCriticalStock(articleDTO.criticalStock());
        article.setLocked(articleDTO.locked());
        article.setPriceManuallySet(articleDTO.priceManuallySet());
        article.setPriceReview(null);
        article.setExpirationDate(articleDTO.expirationDate());

        articleRepository.persist(article);

        return ArticleResponseDTO.fromEntity(article);
    }

    public List<ArticleResponseDTO> registerAll(List<ArticleRequestDTO> articles) {
        return articles.stream().map(this::register).toList();
    }

    public ArticleResponseDTO update(String id, ArticleRequestDTO articleDTO) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        if (
            !articleFound.getName().equals(articleDTO.name()) &&
            articleRepository.count("name", articleDTO.name()) != 0
        ) {
            throw new BusinessException(
                Response.Status.CONFLICT,
                "Un article avec ce nom existe déjà"
            );
        }

        BigDecimal newCostPrice = computeCostPrice(articleDTO);
        PriceReview priceReview = evaluatePriceReview(articleFound, articleDTO, newCostPrice);

        articleFound.setName(articleDTO.name());
        articleFound.setIcon(articleDTO.icon());
        articleFound.setColor(articleDTO.color());
        articleFound.setCategory(articleDTO.category());
        articleFound.setPrice(articleDTO.price());
        articleFound.setMaxPrice(articleDTO.maxPrice());
        articleFound.setMinPrice(articleDTO.minPrice());
        articleFound.setMarketPrice(articleDTO.marketPrice());
        articleFound.setQuantity(articleDTO.quantity());
        articleFound.setPurchasePrice(articleDTO.purchasePrice());
        articleFound.setTransport(articleDTO.transport());
        articleFound.setMisc(articleDTO.misc());
        articleFound.setPurchasedQuantity(articleDTO.purchasedQuantity());
        articleFound.setCriticalStock(articleDTO.criticalStock());
        articleFound.setLocked(articleDTO.locked());
        articleFound.setPriceManuallySet(articleDTO.priceManuallySet());
        articleFound.setCostPrice(newCostPrice);
        articleFound.setPriceReview(priceReview);
        articleFound.setExpirationDate(articleDTO.expirationDate());

        articleRepository.update(articleFound);

        return ArticleResponseDTO.fromEntity(articleFound);
    }

    public ArticleResponseDTO toggleArchive(String id) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        articleFound.setArchived(!articleFound.isArchived());
        articleRepository.update(articleFound);

        return ArticleResponseDTO.fromEntity(articleFound);
    }

    public ArticleResponseDTO decrementQuantity(String id, int quantityOrdered) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        articleFound.setQuantity(articleFound.getQuantity() - quantityOrdered);
        articleRepository.update(articleFound);

        return ArticleResponseDTO.fromEntity(articleFound);
    }

    public ArticleResponseDTO receiveStock(String id, int qty, BigDecimal purchasePrice, BigDecimal transportShare) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        BigDecimal newCostPrice = purchasePrice.add(transportShare)
            .divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP);
        PriceReview priceReview = evaluatePriceReviewForCostChange(articleFound, newCostPrice);

        articleFound.setQuantity(articleFound.getQuantity() + qty);
        articleFound.setPurchasePrice(purchasePrice);
        articleFound.setTransport(transportShare);
        articleFound.setMisc(BigDecimal.ZERO);
        articleFound.setPurchasedQuantity(qty);
        articleFound.setCostPrice(newCostPrice);
        articleFound.setPriceReview(priceReview);

        articleRepository.update(articleFound);

        return ArticleResponseDTO.fromEntity(articleFound);
    }

    /**
     * Déclenchée par un lot reçu (validation d'une quête) — systématique, sans seuil ni condition
     * de verrouillage : dès que le coût de revient change, l'article ressort en rouge dans
     * Administration pour que l'admin vienne le vérifier avec le contexte réel de l'achat sous
     * les yeux. Un article jamais acheté avant (pas de coût antérieur) compte comme un coût de 0 —
     * son tout premier achat réel déclenche donc aussi la revue. Ne remonte rien seulement si le
     * coût est resté strictement identique.
     */
    private PriceReview evaluatePriceReviewForCostChange(Article existing, BigDecimal newCostPrice) {
        BigDecimal previousCost = existing.getCostPrice() != null ? existing.getCostPrice() : BigDecimal.ZERO;
        if (newCostPrice.compareTo(previousCost) == 0) {
            return null;
        }

        BigDecimal recommendedPrice = newCostPrice.multiply(TARGET_MARGIN).setScale(0, RoundingMode.HALF_UP);
        BigDecimal marginDrop = newCostPrice.subtract(previousCost).max(BigDecimal.ZERO);

        return new PriceReview(existing.getPrice(), newCostPrice, recommendedPrice, marginDrop);
    }

    public ArticleResponseDTO incrementQuantity(String id, int quantityAdded) {
        Article articleFound = articleRepository.findById(new ObjectId(id));

        if (articleFound == null) {
            throw new BusinessException(
                Response.Status.NOT_FOUND,
                "Article not found " + id
            );
        }

        if (quantityAdded <= 0) {
            throw new BusinessException(
                Response.Status.BAD_REQUEST,
                "La quantité à ajouter doit être positive"
            );
        }

        articleFound.setQuantity(articleFound.getQuantity() + quantityAdded);
        articleRepository.update(articleFound);

        return ArticleResponseDTO.fromEntity(articleFound);
    }
}
