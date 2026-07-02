package org.acme.Entity;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Unité de vente d'un article — détermine si le front propose un stepper entier ou des paliers kg. */
public enum Unit {
    @JsonProperty("piece")
    PIECE,
    @JsonProperty("kg")
    KG
}
