package org.acme.Service.Flyer.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.FlyerRequestDTO;
import org.acme.DTO.FlyerResponseDTO;
import org.acme.DTO.FlyerSectionRequestDTO;
import org.acme.DTO.FlyerSectionResponseDTO;
import org.acme.DTO.GammeResponseDTO;
import org.acme.Entity.Flyer;
import org.acme.Entity.FlyerSection;
import org.acme.Exception.BusinessException;
import org.acme.Repository.FlyerRepository;
import org.acme.Service.Flyer.FlyerService;
import org.acme.Service.Gamme.GammeService;
import org.bson.types.ObjectId;

@ApplicationScoped
public class FlyerServiceImpl implements FlyerService {

    private final FlyerRepository flyerRepository;
    private final GammeService gammeService;

    @Inject
    public FlyerServiceImpl(FlyerRepository _flyerRepository, GammeService _gammeService) {
        this.flyerRepository = _flyerRepository;
        this.gammeService = _gammeService;
    }

    @Override
    public FlyerResponseDTO create(FlyerRequestDTO request) {
        List<FlyerSection> sections = toSections(request.sections());

        Flyer flyer = new Flyer();
        flyer.setTitle(request.title());
        flyer.setSections(sections);
        flyer.setCreatedAt(Instant.now());
        flyer.setUpdatedAt(flyer.getCreatedAt());
        flyerRepository.persist(flyer);

        return toResponseDTO(flyer);
    }

    @Override
    public FlyerResponseDTO update(String id, FlyerRequestDTO request) {
        Flyer flyer = findEntityById(id);
        List<FlyerSection> sections = toSections(request.sections());

        flyer.setTitle(request.title());
        flyer.setSections(sections);
        flyer.setUpdatedAt(Instant.now());
        flyerRepository.update(flyer);

        return toResponseDTO(flyer);
    }

    @Override
    public List<FlyerResponseDTO> listAll() {
        return flyerRepository.listAll().stream().map(this::toResponseDTO).toList();
    }

    @Override
    public FlyerResponseDTO findById(String id) {
        return toResponseDTO(findEntityById(id));
    }

    private Flyer findEntityById(String id) {
        Flyer flyer = flyerRepository.findById(new ObjectId(id));
        if (flyer == null) {
            throw new BusinessException(Response.Status.NOT_FOUND, "Flyer not found " + id);
        }
        return flyer;
    }

    /** Valide que chaque gamme référencée existe encore et construit les sections à persister. */
    private List<FlyerSection> toSections(List<FlyerSectionRequestDTO> requestSections) {
        return requestSections.stream()
            .map(section -> {
                try {
                    gammeService.findById(section.gammeId());
                } catch (BusinessException e) {
                    throw new BusinessException(
                        Response.Status.BAD_REQUEST,
                        "Gamme inconnue : " + section.gammeId()
                    );
                }
                return new FlyerSection(section.title(), section.gammeId());
            })
            .toList();
    }

    private FlyerResponseDTO toResponseDTO(Flyer flyer) {
        List<FlyerSectionResponseDTO> sections = flyer.getSections() == null
            ? List.of()
            : flyer.getSections().stream()
                .map(this::toSectionResponseDTO)
                .filter(section -> section != null)
                .toList();

        return new FlyerResponseDTO(
            flyer.id.toHexString(),
            flyer.getTitle(),
            sections,
            flyer.getCreatedAt(),
            flyer.getUpdatedAt()
        );
    }

    private FlyerSectionResponseDTO toSectionResponseDTO(FlyerSection section) {
        try {
            GammeResponseDTO gamme = gammeService.findById(section.getGammeId());
            return new FlyerSectionResponseDTO(section.getTitle(), gamme);
        } catch (BusinessException e) {
            // La gamme a été supprimée depuis la création du flyer — on ignore cette section plutôt que de planter.
            return null;
        }
    }
}
