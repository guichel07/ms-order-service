package org.acme.DTO;

/** Vue enrichie d'un article vendu par ce fournisseur — nom/icône lus depuis le catalogue actuel. */
public record SupplierArticleDTO(
    String articleId,
    String name,
    String icon
) {}
