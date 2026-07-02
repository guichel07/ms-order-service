package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fiche fournisseur : un annuaire pour savoir qui vend quoi et si c'est fiable — sans ça,
 * personne ne sait qui a été vu lors d'une mission d'achat, ni si ça vaut le coup d'y retourner.
 * Rattaché à des articles précis (pas une catégorie) pour permettre plus tard un matching fiable
 * entre "cet article est en rupture" et "quel fournisseur le vend".
 */
@MongoEntity(collection = "Suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Supplier extends PanacheMongoEntity {

    private String name;

    private String phone;

    private List<String> articleIds;

    private List<SupplierNote> notes;

    private Instant createdAt;

    private Instant updatedAt;
}
