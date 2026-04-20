package com.ecommerce.api.product.entity;

import com.ecommerce.api.media.entity.UploadedImage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "product_image",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_image_uploaded_image",
                        columnNames = "uploaded_image_id"
                ),
                @UniqueConstraint(
                        name = "uk_product_image_product_id_display_order",
                        columnNames = { "product_id", "display_order" }
                )
        }
)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK는 직접 설정
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_image_id", nullable = false)
    private UploadedImage uploadedImage;

    public ProductImage(Long productId, int displayOrder, UploadedImage uploadedImage) {
        this.productId = productId;
        this.displayOrder = displayOrder;
        this.uploadedImage = uploadedImage;
        uploadedImage.markAttached();
    }
}
