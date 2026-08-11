package com.ecommerce.api.support;

import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductCategory;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import org.springframework.test.util.ReflectionTestUtils;

public final class UnitTestFixtures {

    private UnitTestFixtures() {
    }

    public static User buyer() {
        return user(UserRole.BUYER);
    }

    public static User seller() {
        return user(UserRole.SELLER);
    }

    private static User user(UserRole role) {
        String roleName = role.name().toLowerCase();
        return User.join(roleName + "email", roleName + "nickname", "encPassword", role);
    }

    public static Product product() {
        return Product.register(
                "productName",
                new ProductCategory("productCategory"),
                "description",
                1000L,
                seller()
        );
    }

    public static CartItem cartItem(int quantity) {
        return new CartItem(buyer(), product(), quantity);
    }

    public static CartItem cartItemWithId(Long id, int quantity) {
        CartItem cartItem = cartItem(quantity);
        setEntityId(cartItem, id);
        return cartItem;
    }

    private static void setEntityId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
