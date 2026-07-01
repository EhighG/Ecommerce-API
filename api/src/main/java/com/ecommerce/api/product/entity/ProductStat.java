package com.ecommerce.api.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class ProductStat {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private long viewCount = 0L;

    @Column(nullable = false)
    private long orderItemCount = 0L;

    @Column(nullable = false)
    private long reviewCount = 0L;

    @Column(nullable = false)
    private long ratingSum = 0L;

    @Column(nullable = false)
    private double ratingAvg = 0.0;

    public ProductStat(Product product) {
        this.product = product;
    }
}
