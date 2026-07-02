package org.acme.Service.Mission.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import org.acme.DTO.MissionRequestDTO;
import org.acme.DTO.MissionResponseDTO;
import org.acme.DTO.MissionUpdateDTO;
import org.acme.DTO.SupplierNoteRequestDTO;
import org.acme.DTO.SupplierRequestDTO;
import org.acme.DTO.SupplierResponseDTO;
import org.acme.Entity.Article;
import org.acme.Entity.Mission;
import org.acme.Entity.Queste;
import org.acme.Exception.BusinessException;
import org.acme.Repository.ArticleRepository;
import org.acme.Repository.MissionRepository;
import org.acme.Repository.QuesteRepository;
import org.acme.Service.Mission.MissionService;
import org.acme.Service.Supplier.SupplierService;
import org.bson.types.ObjectId;
import java.util.List;

@ApplicationScoped
public class MissionServiceImpl implements MissionService {

    private static final String STATUS_EN_COURS = "en_cours";
    private static final String STATUS_TERMINEE = "terminee";

    private final MissionRepository missionRepository;
    private final QuesteRepository questeRepository;
    private final ArticleRepository articleRepository;
    private final SupplierService supplierService;

    @Inject
    public MissionServiceImpl(
        MissionRepository _missionRepository,
        QuesteRepository _questeRepository,
        ArticleRepository _articleRepository,
        SupplierService _supplierService
    ) {
        this.missionRepository = _missionRepository;
        this.questeRepository = _questeRepository;
        this.articleRepository = _articleRepository;
        this.supplierService = _supplierService;
    }

    @Override
    public MissionResponseDTO create(String questeId, MissionRequestDTO request) {
        Queste queste = findQuesteById(questeId);
        if (!STATUS_EN_COURS.equals(queste.getStatus())) {
            throw new BusinessException(Response.Status.BAD_REQUEST, "Cette quête n'est plus modifiable");
        }

        Article article = articleRepository.findById(new ObjectId(request.articleId()));
        if (article == null) {
            throw new BusinessException(Response.Status.BAD_REQUEST, "Article inconnu : " + request.articleId());
        }

        Mission mission = new Mission();
        mission.setQuesteId(questeId);
        mission.setArticleId(request.articleId());
        mission.setName(article.getName());
        mission.setIcon(article.getIcon());
        mission.setIconColor(article.getColor());
        mission.setQty(request.qty());
        mission.setPricePaid(request.pricePaid() != null ? request.pricePaid() : BigDecimal.ZERO);
        mission.setRefUnitCost(article.getCostPrice() != null ? article.getCostPrice() : BigDecimal.ZERO);
        mission.setStatus(request.status() != null ? request.status() : STATUS_TERMINEE);
        mission.setCreatedAt(Instant.now());
        mission.setMarketPrice(request.marketPrice());

        resolveSupplier(mission, request.supplierId(), request.newSupplierName(), request.newSupplierPhone(), queste.getNumber());
        applyMarketPrice(article, request.marketPrice());

        missionRepository.persist(mission);

        return toResponseDTO(mission);
    }

    @Override
    public MissionResponseDTO update(String id, MissionUpdateDTO request) {
        Mission mission = findMissionById(id);
        Queste queste = findQuesteById(mission.getQuesteId());
        if (!STATUS_EN_COURS.equals(queste.getStatus())) {
            throw new BusinessException(Response.Status.BAD_REQUEST, "Cette quête n'est plus modifiable");
        }

        mission.setQty(request.qty());
        mission.setPricePaid(request.pricePaid() != null ? request.pricePaid() : BigDecimal.ZERO);
        if (request.status() != null) {
            mission.setStatus(request.status());
        }
        if (request.marketPrice() != null) {
            mission.setMarketPrice(request.marketPrice());
        }

        resolveSupplier(mission, request.supplierId(), request.newSupplierName(), request.newSupplierPhone(), queste.getNumber());
        missionRepository.update(mission);

        if (request.marketPrice() != null) {
            Article article = articleRepository.findById(new ObjectId(mission.getArticleId()));
            applyMarketPrice(article, request.marketPrice());
        }

        return toResponseDTO(mission);
    }

    /**
     * Rattache la mission à un fournisseur (existant ou créé à la volée) si demandé, rattache
     * l'article à sa fiche, et n'ajoute une note d'historique que si le fournisseur a réellement
     * changé (ou est renseigné pour la première fois) — évite de spammer l'historique à chaque
     * simple correction de quantité/prix sur une mission déjà rattachée.
     */
    private void resolveSupplier(Mission mission, String supplierId, String newSupplierName, String newSupplierPhone, int questeNumber) {
        SupplierResponseDTO supplier;

        if (newSupplierName != null && !newSupplierName.isBlank()) {
            if (newSupplierPhone == null || newSupplierPhone.isBlank()) {
                throw new BusinessException(
                    Response.Status.BAD_REQUEST,
                    "Le téléphone est requis pour créer un fournisseur à la volée"
                );
            }
            supplier = supplierService.create(
                new SupplierRequestDTO(newSupplierName, newSupplierPhone, List.of(mission.getArticleId()))
            );
        } else if (supplierId != null && !supplierId.isBlank()) {
            supplier = supplierService.linkArticle(supplierId, mission.getArticleId());
        } else {
            return;
        }

        boolean supplierChanged = !supplier.id().equals(mission.getSupplierId());
        mission.setSupplierId(supplier.id());
        mission.setSupplierName(supplier.name());

        if (!supplierChanged) {
            return;
        }

        supplierService.addNote(
            supplier.id(),
            new SupplierNoteRequestDTO(
                "Fourni " + mission.getName() + " : " + mission.getQty() + " x pour " + mission.getPricePaid() + " F (Quête #" + questeNumber + ")"
            )
        );
    }

    /** Met à jour le prix marché de l'article si une valeur fraîche a été observée sur le terrain. */
    private void applyMarketPrice(Article article, BigDecimal marketPrice) {
        if (marketPrice == null || article == null) {
            return;
        }
        article.setMarketPrice(marketPrice);
        articleRepository.update(article);
    }

    private Mission findMissionById(String id) {
        Mission mission = missionRepository.findById(new ObjectId(id));
        if (mission == null) {
            throw new BusinessException(Response.Status.NOT_FOUND, "Mission not found " + id);
        }
        return mission;
    }

    private Queste findQuesteById(String id) {
        Queste queste = questeRepository.findById(new ObjectId(id));
        if (queste == null) {
            throw new BusinessException(Response.Status.NOT_FOUND, "Queste not found " + id);
        }
        return queste;
    }

    public static MissionResponseDTO toResponseDTO(Mission mission) {
        return new MissionResponseDTO(
            mission.id.toHexString(),
            mission.getQuesteId(),
            mission.getArticleId(),
            mission.getName(),
            mission.getIcon(),
            mission.getIconColor(),
            mission.getSupplierId(),
            mission.getSupplierName(),
            mission.getQty(),
            mission.getPricePaid(),
            mission.getRefUnitCost(),
            mission.getStatus(),
            mission.getCreatedAt(),
            mission.getMarketPrice()
        );
    }
}
