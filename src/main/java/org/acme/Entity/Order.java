package org.acme.Entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@MongoEntity(collection = "Orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order extends PanacheMongoEntity {

    private String receiptNumber;
    private Date receiptDate;
    private String sellerName;
    private String email;
    private BigDecimal dailySummary;
    private Instant saleDate;
    private ArrayList<OrderItem> articles;
}
