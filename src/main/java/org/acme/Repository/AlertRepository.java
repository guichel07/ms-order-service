package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.acme.Entity.Alert;

@ApplicationScoped
public class AlertRepository implements PanacheMongoRepository<Alert> {

    public List<Alert> listAllNewestFirst() {
        return listAll().stream()
            .sorted((a, b) -> b.getDetectedAt().compareTo(a.getDetectedAt()))
            .toList();
    }
}
