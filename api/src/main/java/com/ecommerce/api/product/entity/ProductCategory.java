package com.ecommerce.api.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_category",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_category_name", columnNames = "name"
                )
        }
)
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String name;

    public ProductCategory(String name) {
        this.name = name;
    }
}
