package org.acme.Service.Queste.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.acme.DTO.MissionResponseDTO;
import org.acme.DTO.QuesteClosureRequestDTO;
import org.acme.DTO.QuesteRequestDTO;
import org.acme.DTO.QuesteResponseDTO;
import org.acme.Entity.Mission;
import org.acme.Entity.Queste;
import org.acme.Exception.BusinessException;
import org.acme.Repository.MissionRepository;
import org.acme.Repository.QuesteRepository;
import org.acme.Service.Article.ArticleService;
import org.acme.Service.Mission.impl.MissionServiceImpl;
import org.acme.Service.Queste.QuesteService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class QuesteServiceImpl implements QuesteService {

    private static final String STATUS_EN_COURS = "en_cours";
    private static final String STATUS_EN_ATTENTE_VALIDATION = "en_attente_validation";
    private static final String STATUS_CLOTUREE = "cloturee";
    private static final String STATUS_TERMINEE = "terminee";

    private final QuesteRepository questeRepository;
    private final MissionRepository missionRepository;
    private final ArticleService articleService;

    @Inject
    public QuesteServiceImpl(
        QuesteRepository _questeRepository,
        MissionRepository _missionRepository,
        ArticleService _articleService
    ) {
        this.questeRepository = _questeRepository;
        this.missionRepository = _missionRepository;
        this.articleService = _articleService;
    }

    @Override
    public QuesteResponseDTO create(QuesteRequestDTO request) {
        Queste queste = new Queste();
        queste.setNumber(nextNumber());
        queste.setBuyerName(request.buyerName());
        queste.setObjectif(request.objectif());
        queste.setCreatedAt(Instant.now());
        queste.setStatus(STATUS_EN_COURS);
        queste.setPrimePercent(request.primePercent() != null ? request.primePercent() : BigDecimal.ZERO);
        questeRepository.persist(queste);

        return toResponseDTO(queste, List.of());
    }

    @Override
    public List<QuesteResponseDTO> listAll() {
        return questeRepository.listAll().stream()
            .map(queste -> toResponseDTO(queste, missionRepository.listByQueste(queste.id.toHexString())))
            .toList();
    }

    @Override
    public QuesteResponseDTO findById(String id) {
        Queste queste = findEntityById(id);
        return toResponseDTO(queste, missionRepository.listByQueste(id));
    }

    @Override
    public QuesteResponseDTO close(String id, QuesteClosureRequestDTO request) {
        Queste queste = findEntityById(id);
        if (!STATUS_EN_COURS.equals(queste.getStatus())) {
            throw new BusinessException(Response.Status.BAD_REQUEST, "Cette quête est déjà clôturée ou en attente de validation");
        }

        List<Mission> missions = missionRepository.listByQueste(id);
        List<Mission> terminees = missions.stream().filter(m -> STATUS_TERMINEE.equals(m.getStatus())).toList();

        BigDecimal economieAchats = terminees.stream()
            .map(m -> m.getRefUnitCost().multiply(BigDecimal.valueOf(m.getQty())).subtract(m.getPricePaid()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prime = economieAchats.compareTo(BigDecimal.ZERO) > 0
            ? economieAchats.multiply(queste.getPrimePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        queste.setTransportReel(round(request.transportReel() != null ? request.transportReel() : BigDecimal.ZERO));
        queste.setEconomieAchats(round(economieAchats));
        queste.setPrime(round(prime));
        queste.setStatus(STATUS_EN_ATTENTE_VALIDATION);
        queste.setClosedAt(Instant.now());
        questeRepository.update(queste);

        reportUnfinishedMissions(queste, missions);

        return toResponseDTO(queste, missionRepository.listByQueste(id));
    }

    /**
     * Les missions en_attente/ecartee ne disparaissent pas à la clôture : elles sont rattachées
     * à une quête en_cours du même acheteur (existante, sinon créée) pour être reprises sans
     * perte d'information.
     */
    private void reportUnfinishedMissions(Queste closedQueste, List<Mission> missions) {
        List<Mission> unfinished = missions.stream()
            .filter(m -> !STATUS_TERMINEE.equals(m.getStatus()))
            .toList();
        if (unfinished.isEmpty()) {
            return;
        }

        Queste target = questeRepository.listAll().stream()
            .filter(q -> STATUS_EN_COURS.equals(q.getStatus()) && q.getBuyerName().equals(closedQueste.getBuyerName()))
            .findFirst()
            .orElseGet(() -> {
                Queste nouvelle = new Queste();
                nouvelle.setNumber(nextNumber());
                nouvelle.setBuyerName(closedQueste.getBuyerName());
                nouvelle.setCreatedAt(Instant.now());
                nouvelle.setStatus(STATUS_EN_COURS);
                nouvelle.setPrimePercent(closedQueste.getPrimePercent());
                questeRepository.persist(nouvelle);
                return nouvelle;
            });

        String targetId = target.id.toHexString();
        for (Mission mission : unfinished) {
            mission.setQuesteId(targetId);
            missionRepository.update(mission);
        }
    }

    @Override
    public QuesteResponseDTO validate(String id) {
        Queste queste = findEntityById(id);
        if (!STATUS_EN_ATTENTE_VALIDATION.equals(queste.getStatus())) {
            throw new BusinessException(Response.Status.BAD_REQUEST, "Cette quête n'est pas en attente de validation");
        }

        List<Mission> terminees = missionRepository.listByQueste(id).stream()
            .filter(m -> STATUS_TERMINEE.equals(m.getStatus()))
            .toList();

        BigDecimal totalAchatReel = terminees.stream()
            .map(Mission::getPricePaid)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Regroupées par article avant réception : si une même quête contient plusieurs missions
        // terminées sur le même article, les traiter une par une écraserait la comparaison de
        // revue de prix (chaque appel comparerait au coût déjà mis à jour par le précédent, pas
        // au coût réellement antérieur à la quête) — un seul appel agrégé par article corrige ça.
        Map<String, List<Mission>> parArticle = terminees.stream()
            .collect(Collectors.groupingBy(Mission::getArticleId));

        for (Map.Entry<String, List<Mission>> entry : parArticle.entrySet()) {
            List<Mission> missions = entry.getValue();
            int qtyTotal = missions.stream().mapToInt(Mission::getQty).sum();
            BigDecimal pricePaidTotal = missions.stream()
                .map(Mission::getPricePaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal transportShare = totalAchatReel.compareTo(BigDecimal.ZERO) > 0
                ? queste.getTransportReel().multiply(pricePaidTotal)
                    .divide(totalAchatReel, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            articleService.receiveStock(entry.getKey(), qtyTotal, pricePaidTotal, transportShare);
        }

        queste.setStatus(STATUS_CLOTUREE);
        queste.setValidatedAt(Instant.now());
        questeRepository.update(queste);

        return toResponseDTO(queste, missionRepository.listByQueste(id));
    }

    private Queste findEntityById(String id) {
        Queste queste = questeRepository.findById(new ObjectId(id));
        if (queste == null) {
            throw new BusinessException(Response.Status.NOT_FOUND, "Queste not found " + id);
        }
        return queste;
    }

    private int nextNumber() {
        return questeRepository.listAll().stream().mapToInt(Queste::getNumber).max().orElse(0) + 1;
    }

    private QuesteResponseDTO toResponseDTO(Queste queste, List<Mission> missions) {
        List<MissionResponseDTO> missionDTOs = missions.stream().map(MissionServiceImpl::toResponseDTO).toList();

        return new QuesteResponseDTO(
            queste.id.toHexString(),
            queste.getNumber(),
            queste.getBuyerName(),
            queste.getObjectif(),
            queste.getCreatedAt(),
            queste.getStatus(),
            queste.getTransportReel(),
            queste.getPrimePercent(),
            queste.getEconomieAchats(),
            queste.getPrime(),
            queste.getClosedAt(),
            queste.getValidatedAt(),
            missionDTOs
        );
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
