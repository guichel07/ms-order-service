package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.Entity.Article;

/**
 * ArticleRepository
 */
@ApplicationScoped
public class ArticleRepository implements PanacheMongoRepository<Article> {

    public Article findByName(String name) {
        return find("name", name).firstResult();
    }
}
