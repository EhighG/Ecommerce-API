package com.ecommerce.api.product.repository;

import com.ecommerce.api.inventory.entity.QInventory;
import com.ecommerce.api.media.entity.QUploadedImage;
import com.ecommerce.api.product.dto.ProductDetailDto;
import com.ecommerce.api.product.dto.ProductListDto;
import com.ecommerce.api.product.dto.SearchReq;
import com.ecommerce.api.product.entity.QProduct;
import com.ecommerce.api.product.entity.QProductCategory;
import com.ecommerce.api.product.entity.QProductImage;
import com.ecommerce.api.product.entity.QProductStat;
import com.ecommerce.api.user.entity.QUser;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Repository
public class ProductQueryRepositoryImpl implements ProductQueryRepository {

    private final JPAQueryFactory queryFactory;

    private final QProduct product = QProduct.product;
    private final QProductStat productStat = QProductStat.productStat;
    private final QProductCategory category = QProductCategory.productCategory;
    private final QUser seller = QUser.user;
    private final QProductImage productImage = QProductImage.productImage;
    private final QUploadedImage uploadedImage = QUploadedImage.uploadedImage;
    private final QInventory inventory = QInventory.inventory;


    @Override
    public Page<ProductListDto> search(SearchReq condition, Pageable pageable) {
        List<ProductListDto> content = fetchProducts(condition, pageable);

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
                        productStat.ratingAvg
                ))
                .from(product)
                .join(product.category, category)
                .join(product.seller, seller)
                .join(inventory).on(inventory.product.eq(product))
                .join(productStat).on(productStat.product.eq(product))
                .where(
                        product.id.eq(productId),
                        product.deleted.isFalse()
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    private List<ProductListDto> fetchProducts(SearchReq condition, Pageable pageable) {
        return baseSelect()
                .where(baseWhere(condition))
                .orderBy(primaryOrder(condition), product.id.desc())
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
                        productStat.ratingAvg
                ))
                .from(product)
                .join(product.category, category)
                .join(product.seller, seller)
                .join(inventory).on(inventory.product.eq(product))
                .join(productStat).on(productStat.product.eq(product))
                .leftJoin(product.thumbnailImage, productImage)
                .leftJoin(productImage.uploadedImage, uploadedImage);
    }

    private BooleanBuilder baseWhere(SearchReq condition) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(product.deleted.isFalse());

        if(StringUtils.hasText(condition.keyword())) {
            where.and(fullTextSearch(condition.keyword()));
        }

        if (condition.categoryId() != null)
            where.and(product.category.id.eq(condition.categoryId()));
        if (condition.sellerId() != null)
            where.and(product.seller.id.eq(condition.sellerId()));

        return where;
    }

    private BooleanExpression fullTextSearch(String keyword) {
        String fullTextKeyword = toBooleanFullTextKeyword(keyword);

        if (!StringUtils.hasText(fullTextKeyword)) {
            return Expressions.FALSE;
        }

        return Expressions.numberTemplate(
                Double.class,
                "sql('match(?, ?) against (? in boolean mode)', {0}, {1}, {2})",
                product.name,
                product.description,
                fullTextKeyword
        ).gt(0.0);
    }

    /**
     * 공백 포함된 검색어는 토큰이 전부 포함된것만 검색되도록 변환함
     * ex) "파란 반팔" -> "파란", "반팔" 모두 포함
     */
    private String toBooleanFullTextKeyword(String keyword) {
        String sanitized = keyword.replaceAll("[+\\-<>()~*\"@]+", " ");

        return Arrays.stream(sanitized.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .map(token -> "+" + token)
                .collect(Collectors.joining(" "));
    }

    private OrderSpecifier<?> primaryOrder(SearchReq condition) {
        return switch (condition.sortBy()) {
            case ORDER_COUNT -> new OrderSpecifier<>(toOrder(condition), productStat.orderItemCount);
            case RATING -> new OrderSpecifier<>(toOrder(condition), productStat.ratingAvg);
            case VIEW_COUNT -> new OrderSpecifier<>(toOrder(condition), product.viewCount);
            case PRICE -> new OrderSpecifier<>(toOrder(condition), product.unitPrice);
            case REG_DATE -> new OrderSpecifier<>(toOrder(condition), product.createdAt);
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
}
