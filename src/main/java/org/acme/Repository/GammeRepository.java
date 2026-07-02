package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.Entity.Gamme;

@ApplicationScoped
public class GammeRepository implements PanacheMongoRepository<Gamme> {
}
