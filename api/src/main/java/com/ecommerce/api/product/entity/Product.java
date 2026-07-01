package com.ecommerce.api.product.entity;

import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
//@SQLRestriction("deleted = false") // Review쪽에서 삭제된 상품 표시하기 위함
@SQLDelete(sql = "update product set deleted = true where id = ?")
@Table(
        indexes = {
                @Index(
                        name = "idx_product_deleted_created_id",
                        columnList = "deleted, created_at DESC, id DESC"
                ),
                @Index(
                        name = "idx_product_category_deleted_created_id",
                        columnList = "product_category_id, deleted, created_at DESC, id DESC"
                )
        }
)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_category_id", nullable = false)
    private ProductCategory category;

    @Size(min = 1, max = 1000)
    @Column(nullable = false)
    private String description;

    @Min(0)
    @Column(nullable = false)
    private long unitPrice;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_image_id")
    private ProductImage thumbnailImage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false)
    private boolean deleted = false;

    private Product(String name, ProductCategory category, String description, long unitPrice, User seller) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.unitPrice = unitPrice;
        this.seller = seller;
    }

    public static Product register(String name, ProductCategory category, String description, long unitPrice, User seller) {
        // SELLER 권한체크는 엔티티 단에서 말고, 서비스 or 인가 단계에서 한다.
        return new Product(name, category, description, unitPrice, seller);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setUnitPrice(long unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setThumbnailImage(ProductImage thumbnailImage) {
        this.thumbnailImage = thumbnailImage;
    }
}
