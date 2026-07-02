package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.acme.Entity.Movement;

@ApplicationScoped
public class MovementRepository implements PanacheMongoRepository<Movement> {

    public List<Movement> listAllNewestFirst() {
        return listAll().stream()
            .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
            .toList();
    }
}
