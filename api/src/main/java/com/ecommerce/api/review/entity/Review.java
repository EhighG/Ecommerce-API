package com.ecommerce.api.review.entity;

import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "review",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_writer_product", columnNames = {"writer_id", "product_id"})
        }
)
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "writer_id", nullable = false)
    private User writer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Embedded
    private Rating rating;

    @Column(length = 255)
    private String content;

    public Review(User writer, Product product, int halfStars, String content) {
        this.writer = writer;
        this.product = product;
        this.rating = new Rating(halfStars);
        this.content = content;
    }

    public void modify(int halfStars, String content) {
        this.rating = new Rating(halfStars);
        this.content = content;
    }
}
