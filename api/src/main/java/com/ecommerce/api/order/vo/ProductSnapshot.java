package com.ecommerce.api.order.vo;

import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductImage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class ProductSnapshot {

    @Column(name = "product_id", nullable = false)
    private Long id;

    @Column(name = "product_name", nullable = false)
    private String name;

    @Column(name = "product_category_name", nullable = false)
    private String categoryName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "id", column = @Column(name = "seller_id", nullable = false)),
            @AttributeOverride(name = "nickname", column = @Column(name = "seller_nickname", nullable = false))
    })
    private UserSnapshot seller;

    @Lob
    @Column(name = "product_description", nullable = false)
    private String description;

    @Column(name = "product_unit_price", nullable = false)
    private long unitPrice;

    private String thumbnailPath;

    public static ProductSnapshot from(Product product) {
        ProductImage thumbnail = product.getThumbnailImage();
        return ProductSnapshot.builder()
                .id(product.getId())
                .name(product.getName())
                .categoryName(product.getCategory().getName())
                .seller(new UserSnapshot(product.getSeller().getId(), product.getSeller().getNickname()))
                .description(product.getDescription())
                .unitPrice(product.getUnitPrice())
                .thumbnailPath(thumbnail != null ? thumbnail.getUploadedImage().getObjectKey() : null)
                .build();
    }

    @Builder
    private ProductSnapshot(Long id, String name, String categoryName, UserSnapshot seller, String description, long unitPrice, String thumbnailPath) {
        this.id = id;
        this.name = name;
        this.categoryName = categoryName;
        this.seller = seller;
        this.description = description;
        this.unitPrice = unitPrice;
        this.thumbnailPath = thumbnailPath;
    }
}
