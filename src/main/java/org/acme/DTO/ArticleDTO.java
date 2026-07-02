package org.acme.DTO;

import java.math.BigDecimal;

public record ArticleDTO(
    String name,

    String icon,

    String color,

    String category,

    BigDecimal price
) {}
