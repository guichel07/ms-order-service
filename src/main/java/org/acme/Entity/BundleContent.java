package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contenu PDF marketing pré-généré pour une clé de bundle (ex: "matin", "categorie:Hygiène") —
 * stocké pour être régénéré par un job planifié plutôt qu'appelé à chaque affichage. Le bundle
 * lui-même est stocké sérialisé en JSON (bundleJson) plutôt qu'embarqué tel quel : les DTO sont
 * des records, pas des POJO à getters/setters, donc pas directement compatibles avec le codec
 * Mongo Panache par défaut.
 */
@MongoEntity(collection = "BundleContents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BundleContent extends PanacheMongoEntity {

    private String key;

    private String label;

    private String bundleJson;

    private String marketingText;

    private String usageTip;

    private Instant generatedAt;
}
