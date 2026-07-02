package org.acme.Repository;

import java.util.List;
import java.util.Objects;

import org.acme.Entity.AgeCategory;
import org.acme.Entity.Client;
import org.acme.Entity.Gender;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * ClientRepository
 */
@ApplicationScoped
public class ClientRepository implements PanacheMongoRepository<Client> {
    public List<Client> findAllNotArchived() {
        return list("archived", false);
    }

    public Client findByNormalizedPhone(String normalizedPhone) {
        return find("normalizedPhone", normalizedPhone).firstResult();
    }

    public long deleteByNormalizedPhone(String normalizedPhone) {
        return delete("normalizedPhone", normalizedPhone);
    }

    /** gender peut être null (genre inconnu) — filtré côté Java pour gérer ce cas simplement. */
    public Client findAnonymousByAgeCategoryAndGender(AgeCategory ageCategory, Gender gender) {
        return find("anonymous = true and ageCategory = ?1", ageCategory)
            .list()
            .stream()
            .filter(c -> Objects.equals(c.getGender(), gender))
            .findFirst()
            .orElse(null);
    }
}
