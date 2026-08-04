package org.acme.DTO;

import java.math.BigDecimal;

/**
 * label libre, ratio = nb d'unités atomiques que représente ce palier (informatif),
 * price = prix de vente de CE palier, toujours indépendant de ratio × prix unitaire.
 */
public record PackagingLevelDTO(String label, BigDecimal ratio, BigDecimal price) {}
