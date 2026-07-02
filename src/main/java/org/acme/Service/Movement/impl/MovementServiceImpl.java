package org.acme.Service.Movement.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import org.acme.DTO.MovementRequestDTO;
import org.acme.DTO.MovementResponseDTO;
import org.acme.Entity.Movement;
import org.acme.Repository.MovementRepository;
import org.acme.Service.Movement.MovementService;

@ApplicationScoped
public class MovementServiceImpl implements MovementService {

    private final MovementRepository movementRepository;

    public MovementServiceImpl(MovementRepository movementRepository) {
        this.movementRepository = movementRepository;
    }

    @Override
    public List<MovementResponseDTO> listAll() {
        return movementRepository
            .listAllNewestFirst()
            .stream()
            .map(MovementResponseDTO::fromEntity)
            .toList();
    }

    @Override
    @Transactional
    public MovementResponseDTO create(MovementRequestDTO request) {
        Movement movement = new Movement();
        movement.setType(request.type());
        movement.setAmount(request.amount());
        movement.setLabel(request.label().trim());
        movement.setDate(Instant.now());
        movement.setProofImage(request.proofImage());

        movementRepository.persist(movement);

        return MovementResponseDTO.fromEntity(movement);
    }
}
