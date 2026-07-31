package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import org.acme.Entity.Order;

/**
 * OrderRepository
 */
@ApplicationScoped
public class OrderRepository implements PanacheMongoRepository<Order> {

    public List<Order> findBySaleDateRange(Instant start, Instant endExclusive) {
        return list("saleDate >= ?1 and saleDate < ?2", start, endExclusive);
    }

    public List<Order> findByClientId(String clientId) {
        return list("clientId", clientId);
    }

    public List<Order> findByEmail(String email) {
        return list("email", email);
    }

    /** Toute commande contenant au moins une ligne pour cet article. */
    public List<Order> findByArticleId(String articleId) {
        return list("articles.articleId", articleId);
    }
}
