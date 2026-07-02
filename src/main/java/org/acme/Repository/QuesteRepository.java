package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.Entity.Queste;

@ApplicationScoped
public class QuesteRepository implements PanacheMongoRepository<Queste> {
}
