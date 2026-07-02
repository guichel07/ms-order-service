package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.acme.Entity.Mission;

@ApplicationScoped
public class MissionRepository implements PanacheMongoRepository<Mission> {

    public List<Mission> listByQueste(String questeId) {
        return list("questeId", questeId);
    }
}
