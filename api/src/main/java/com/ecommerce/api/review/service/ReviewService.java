package com.ecommerce.api.review.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.order.service.OrderItemService;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.repository.ProductStatRepository;
import com.ecommerce.api.product.service.ProductService;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import com.ecommerce.api.review.dto.*;
import com.ecommerce.api.review.entity.Review;
import com.ecommerce.api.review.repository.ReviewRepository;
import com.ecommerce.api.user.dto.UserReviewListRes;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductStatRepository productStatRepository;
    private final ProductService productService;
    private final OrderItemService orderItemService;
    private final ProductImageUrlResolver productImageUrlResolver;

    @Transactional
    public Long write(WriteReviewReq req, Long userId) {
        checkReviewable(req.productId(), userId);

        User user = getUserNotDeleted(userId);
        Product product = productService.getProduct(req.productId());

        Review saved = reviewRepository.save(
                new Review(user, product, req.halfStars(), req.content())
        );

        int updatedCount = productStatRepository.addReview(product.getId(), req.halfStars());
        if (updatedCount != 1) {
            throw new AppException(PRODUCT_STAT_NOT_FOUND);
        }

        return saved.getId();
    }
    
    private void checkReviewable(Long productId, Long userId) {
        // 1. 유효한 product가 있는지 검사
        Product product = productService.getProduct(productId);

        // 2. 해당 product에 대한 유저의 구매확정 주문건이 있는지 검사
        boolean confirmedOrderExists = orderItemService.confirmedOrderItemExists(userId, productId);
        if (!confirmedOrderExists) {
            throw new AppException(NO_CONFIRMED_ORDER_EXISTS);
        }
        // 3. 기존 리뷰가 있는지 검사
        boolean reviewExists = reviewRepository.findByProductIdAndWriterId(productId, userId)
                .isPresent();
        if (reviewExists) {
            throw new AppException(REVIEW_ALREADY_EXISTS);
        }
    }

    public ReviewSearchRes search(SearchReq req) {
        PageRequest pageable = PageRequest.of(req.page(), req.size());

        if (req.searchBy() == SearchReq.SearchBy.PRODUCT) {
            if (!productService.existsNotDeleted(req.productId())) {
                throw new AppException(PRODUCT_NOT_FOUND);
            }

            Page<ProductReviewListRes> page = reviewRepository.findByProductIdOrderByCreatedAtDesc(req.productId(), pageable)
                    .map(ProductReviewListRes::from);

            return ReviewSearchRes.forProduct(page);
        }

        getUserNotDeleted(req.writerId());

        Page<UserReviewListRes> page = reviewRepository.findByWriterIdOrderByCreatedAtDesc(req.writerId(), pageable)
                .map(review -> UserReviewListRes.of(
                        review,
                        productImageUrlResolver.resolveThumbnail(review.getProduct())
                ));

        return ReviewSearchRes.forUser(page);
    }

    public List<UserReviewListRes> findRecentUserReviews(Long userId) {
        getUserNotDeleted(userId);

        return reviewRepository.findTop5ByWriterIdOrderByCreatedAtDesc(userId).stream()
                .map(review -> UserReviewListRes.of(
                        review,
                        productImageUrlResolver.resolveThumbnail(review.getProduct())
                ))
                .toList();
    }

    public long countByWriter(Long writerId) {
        getUserNotDeleted(writerId);
        return reviewRepository.countByWriterId(writerId);
    }

    public Review getReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new AppException(REVIEW_NOT_FOUND));
    }

    @Transactional
    public void modifyReview(ModifyReviewReq req, Long userId) {
        Review review = getReview(req.reviewId());
        checkWriterMatched(review, userId);

        if (review.getProduct().isDeleted()) {
            throw new AppException(DELETED_PRODUCT);
        }

        int oldHalfStars = review.getRating().getHalfStars();

        review.modify(req.halfStars(), req.content());

        int deltaHalfStars = req.halfStars() - oldHalfStars;

        if (deltaHalfStars == 0) return;

        int updatedCount = productStatRepository.changeReviewRating(review.getProduct().getId(), deltaHalfStars);
        if (updatedCount != 1) {
            throw new AppException(PRODUCT_STAT_NOT_FOUND);
        }
    }

    private void checkWriterMatched(Review review, Long userId) {
        if (!review.getWriter().getId().equals(userId)) {
            throw new AppException(REVIEW_WRITER_MISMATCH);
        }
    }

    private User getUserNotDeleted(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND));
    }
}
