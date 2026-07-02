package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Article
 */
@MongoEntity(collection = "Articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Article extends PanacheMongoEntity {

    private String name;

    private String icon;

    private String color;

    private String category;

    private BigDecimal price;

    private int quantity;
}
