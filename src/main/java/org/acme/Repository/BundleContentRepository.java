package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.Entity.BundleContent;

@ApplicationScoped
public class BundleContentRepository implements PanacheMongoRepository<BundleContent> {

    public BundleContent findByKey(String key) {
        return find("key", key).firstResult();
    }
}
