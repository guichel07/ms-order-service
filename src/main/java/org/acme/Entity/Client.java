package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Client
 */
@MongoEntity(collection = "Clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client extends PanacheMongoEntity {

    private String firstname;

    private String lastname;

    private String phone;

    /** Téléphone normalisé (voir PhoneNormalizer) — clé de rapprochement, jamais affiché. */
    private String normalizedPhone;

    private boolean archived;

    /** Catégorie d'âge — sert à classer les préférences d'achat (enfant/ado/adulte). */
    private AgeCategory ageCategory;

    /** Genre — même usage que ageCategory, peut être inconnu (null). */
    private Gender gender;

    /**
     * Fiche partagée pour les ventes sans numéro de téléphone (ex: enfant envoyé faire
     * une course). Une seule fiche "anonyme" par couple (AgeCategory, Gender) — voir
     * ClientRepository.findAnonymousByAgeCategoryAndGender. N'a jamais de phone/normalizedPhone.
     */
    private boolean anonymous;

}
