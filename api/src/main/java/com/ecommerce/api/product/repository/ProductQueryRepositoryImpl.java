package com.ecommerce.api.product.repository;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.inventory.entity.QInventory;
import com.ecommerce.api.media.entity.QUploadedImage;
import com.ecommerce.api.order.entity.QOrderItem;
import com.ecommerce.api.product.dto.ProductDetailDto;
import com.ecommerce.api.product.dto.ProductListDto;
import com.ecommerce.api.product.dto.SearchReq;
import com.ecommerce.api.product.entity.QProduct;
import com.ecommerce.api.product.entity.QProductCategory;
import com.ecommerce.api.product.entity.QProductImage;
import com.ecommerce.api.review.entity.QReview;
import com.ecommerce.api.user.entity.QUser;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.ecommerce.api.order.enums.OrderStatus.CANCELED;

@RequiredArgsConstructor
@Repository
public class ProductQueryRepositoryImpl implements ProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    private final QProduct product = QProduct.product;
    private final QProductCategory category = QProductCategory.productCategory;
    private final QUser seller = QUser.user;
    private final QProductImage productImage = QProductImage.productImage;
    private final QUploadedImage uploadedImage = QUploadedImage.uploadedImage;
    private final QOrderItem orderItem = QOrderItem.orderItem;
    private final QReview review = QReview.review;
    private final QInventory inventory = QInventory.inventory;


    @Override
    public Page<ProductListDto> search(SearchReq condition, Pageable pageable) {
        List<ProductListDto> content = switch (condition.sortBy()) {
            case VIEW_COUNT, REG_DATE, PRICE -> fetchBaseSorted(condition, pageable);
            case ORDER_COUNT -> fetchOrderCountSorted(condition, pageable);
            case RATING -> fetchRatingSorted(condition, pageable);
        };

        long total = fetchTotal(condition);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Optional<ProductDetailDto> findProductDetail(Long productId) {
        ProductDetailDto result = queryFactory
                .select(Projections.constructor(
                        ProductDetailDto.class,
                        product.id,
                        product.name,
                        category.id,
                        category.name,
                        product.unitPrice,
                        product.description,
                        seller.id,
                        seller.nickname,
                        inventory.quantity,
                        avgHalfStars()
                ))
                .from(product)
                .join(product.category, category)
                .join(product.seller, seller)
                .join(inventory).on(inventory.product.eq(product))
                .leftJoin(review).on(review.product.eq(product))
                .where(
                        product.id.eq(productId),
                        product.deleted.isFalse()
                )
                .groupBy(
                        product.id,
                        product.name,
                        category.id,
                        category.name,
                        product.unitPrice,
                        product.description,
                        seller.id,
                        seller.nickname,
                        inventory.quantity
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    private List<ProductListDto> fetchBaseSorted(SearchReq condition, Pageable pageable) {
        OrderSpecifier<?> primaryOrder = switch (condition.sortBy()) {
            case VIEW_COUNT -> new OrderSpecifier<>(toOrder(condition), product.viewCount);
            case REG_DATE -> new OrderSpecifier<>(toOrder(condition), product.createdAt);
            case PRICE -> new OrderSpecifier<>(toOrder(condition), product.unitPrice);
            default -> throw new AppException(ErrorCode.INVALID_SORT_TYPE);
        };

        return baseSelect()
                .leftJoin(review).on(review.product.eq(product))
                .where(baseWhere(condition))
                .groupBy(productListGroupBy())
                .orderBy(primaryOrder, product.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private List<ProductListDto> fetchOrderCountSorted(SearchReq condition, Pageable pageable) {
        NumberExpression<Long> orderCount = orderItem.id.countDistinct();

        return baseSelect()
                .leftJoin(review).on(review.product.eq(product))
                .leftJoin(orderItem).on(
                        orderItem.product.id.eq(product.id),
                        orderItem.status.ne(CANCELED)
                )
                .where(baseWhere(condition))
                .groupBy(productListGroupBy())
                .orderBy(
                        new OrderSpecifier<>(toOrder(condition), orderCount),
                        product.id.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private List<ProductListDto> fetchRatingSorted(SearchReq condition, Pageable pageable) {
        return baseSelect()
                .leftJoin(review).on(review.product.eq(product))
                .where(baseWhere(condition))
                .groupBy(productListGroupBy())
                .orderBy(
                        new OrderSpecifier<>(toOrder(condition), avgHalfStars()),
                        product.id.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private JPAQuery<ProductListDto> baseSelect() {
        return queryFactory
                .select(Projections.constructor(
                        ProductListDto.class,
                        product.id,
                        product.name,
                        category.name,
                        uploadedImage.objectKey,
                        product.unitPrice,
                        seller.id,
                        seller.nickname,
                        inventory.quantity,
                        avgHalfStars()
                ))
                .from(product)
                .join(product.category, category)
                .join(product.seller, seller)
                .join(inventory).on(inventory.product.eq(product))
                .leftJoin(product.thumbnailImage, productImage)
                .leftJoin(productImage.uploadedImage, uploadedImage);
    }

    private BooleanBuilder baseWhere(SearchReq condition) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(product.deleted.isFalse());

        if(StringUtils.hasText(condition.keyword())) {
            where.and(
                    product.name.containsIgnoreCase(condition.keyword())
                            .or(product.description.containsIgnoreCase(condition.keyword()))
            );
        }

        if (condition.categoryId() != null)
            where.and(product.category.id.eq(condition.categoryId()));
        if (condition.sellerId() != null)
            where.and(product.seller.id.eq(condition.sellerId()));

        return where;
    }

    private Expression<?>[] productListGroupBy() {
        return new Expression<?>[]{
                product.id,
                product.name,
                category.name,
                uploadedImage.objectKey,
                product.unitPrice,
                seller.id,
                seller.nickname,
                inventory.quantity
        };
    }

    private long fetchTotal(SearchReq condition) {
        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(baseWhere(condition))
                .fetchOne();

        return total != null ? total : 0L;
    }

    private Order toOrder(SearchReq condition) {
        return Order.valueOf(condition.direction().name());
    }

    private NumberExpression<Double> avgHalfStars() {
        return review.rating.halfStars.avg().coalesce(0.0);
    }
}
