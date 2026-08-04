package org.acme.Entity;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @deprecated remplacé par {@code Article.atomicUnit}/{@code Article.packagingLevels}
 * (matrice de paliers libre par article). Conservé le temps de la migration des
 * documents existants — à supprimer une fois la prod vérifiée sans champ "unit" résiduel.
 */
@Deprecated
public enum Unit {
    @JsonProperty("piece")
    PIECE,
    @JsonProperty("kg")
    KG
}
