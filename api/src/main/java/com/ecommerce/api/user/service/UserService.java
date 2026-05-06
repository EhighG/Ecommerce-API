package com.ecommerce.api.user.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.order.enums.OrderStatus;
import com.ecommerce.api.order.repository.OrderItemRepository;
import com.ecommerce.api.product.service.ProductDeletionService;
import com.ecommerce.api.review.service.ReviewService;
import com.ecommerce.api.user.dto.*;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import com.ecommerce.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderItemRepository orderItemRepository;
    private final ProductDeletionService productDeletionService;
    private final ReviewService reviewService;

    @Transactional
    public Long join(JoinReq req) {
        if (userRepository.existsByEmail(req.email()))
            throw new AppException(EMAIL_ALREADY_EXISTS);

        User saved = userRepository.save(
                User.join(
                        req.email(),
                        req.nickname(),
                        passwordEncoder.encode(req.password()),
                        req.role()
                )
        );
        return saved.getId();
    };

    public UserProfileRes getUserProfile(Long userId) {
        User user = getUserNotDeleted(userId);

        if (user.getRole() != UserRole.BUYER)
            return new UserProfileRes(user);

        List<UserReviewListRes> recentReviewList = reviewService.findRecentUserReviews(userId);

        long totalReviewCount = reviewService.countByWriter(userId);

        return new UserProfileRes(user, totalReviewCount, recentReviewList);
    }

    public List<UserInfoListRes> getUserInfoList(UserRole currentUserRole) {
        if (!UserRole.ADMIN.equals(currentUserRole))
            throw new AppException(NO_PERMISSIONS);
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(UserInfoListRes::new)
                .toList();
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND));
    }

    public User getUserNotDeleted(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND));
    }

    public User getUserNotDeleted(String email) {
        return userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND));
    }

    @Transactional
    public void modifyInfo(ModifyInfoReq req, Long userId) {
        User user = getUserNotDeleted(userId);
        user.changeNickname(req.nickname());
    }

    /**
     * 성공 후 세션 만료(강제 로그아웃) 포함
     */
    @Transactional
    public void modifyPassword(ModifyPasswordReq req, Long userId) {
        User user = getUserNotDeleted(userId);
        checkPassword(user, req.oldPassword());

        user.changePassword(passwordEncoder.encode(req.newPassword()));
    }

    private void checkPassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPassword()))
            throw new AppException(WRONG_PASSWORD);
    }

    @Transactional
    public void withdraw(WithdrawReq req, Long userId) {
        User user = getUserNotDeleted(userId);
        checkPassword(user, req.password());

        validateWithdrawable(user);
//        leaveAllChatRoom(user);

        if (user.getRole() == UserRole.SELLER)
            productDeletionService.deleteAllBySeller(user.getId());

        user.withdraw();
    }

    private void validateWithdrawable(User user) {
        // 판매자이고, 현재 진행중인 주문(주문완료~배송중)이 있으면 탈퇴불가
        if (user.getRole() == UserRole.SELLER) {
            boolean hasActiveOrder = orderItemRepository.existsByProductSellerIdAndStatuses(
                    user.getId(),
                    List.of(OrderStatus.ORDERED, OrderStatus.SHIPPED)
            );

            if (hasActiveOrder)
                throw new AppException(WITHDRAW_WHEN_ACTIVE_ORDER_EXISTS);
        }
    }
}
