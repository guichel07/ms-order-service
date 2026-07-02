package org.acme.Repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.Entity.Order;

/**
 * OrderRepository
 */
@ApplicationScoped
public class OrderRepository implements PanacheMongoRepository<Order> {}
