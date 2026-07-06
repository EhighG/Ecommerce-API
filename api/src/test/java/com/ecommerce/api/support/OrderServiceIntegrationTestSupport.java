package com.ecommerce.api.support;

import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.cartitem.repository.CartItemRepository;
import com.ecommerce.api.coupon.entity.CouponEvent;
import com.ecommerce.api.coupon.entity.CouponIssued;
import com.ecommerce.api.coupon.enums.CouponStatus;
import com.ecommerce.api.coupon.enums.CouponType;
import com.ecommerce.api.coupon.repository.CouponEventRepository;
import com.ecommerce.api.coupon.repository.CouponIssuedRepository;
import com.ecommerce.api.coupon.repository.OrderItemCouponRepository;
import com.ecommerce.api.idempotency.repository.IdempotencyRecordRepository;
import com.ecommerce.api.inventory.entity.Inventory;
import com.ecommerce.api.inventory.repository.InventoryRepository;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.enums.OrderStatus;
import com.ecommerce.api.order.repository.OrderItemRepository;
import com.ecommerce.api.order.repository.OrderRepository;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductCategory;
import com.ecommerce.api.product.entity.ProductStat;
import com.ecommerce.api.product.repository.ProductCategoryRepository;
import com.ecommerce.api.product.repository.ProductRepository;
import com.ecommerce.api.product.repository.ProductStatRepository;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import com.ecommerce.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.use_sql_comments=false",
        "spring.jpa.properties.hibernate.generate_statistics=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderServiceIntegrationTestConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class OrderServiceIntegrationTestSupport {

    private static final List<String> CLEANUP_TABLES = List.of(
            "idempotency_record",
            "order_item_coupon",
            "review",
            "order_item",
            "orders",
            "coupon_issued",
            "coupon_event",
            "cart_item",
            "inventory",
            "product_stat",
            "product_image",
            "uploaded_image",
            "product",
            "product_category",
            "users"
    );
    private static final AtomicLong TEST_DATA_SEQUENCE = new AtomicLong();

    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("ecommerce_test")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerMysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProductCategoryRepository productCategoryRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected ProductStatRepository productStatRepository;

    @Autowired
    protected InventoryRepository inventoryRepository;

    @Autowired
    protected CartItemRepository cartItemRepository;

    @Autowired
    protected CouponEventRepository couponEventRepository;

    @Autowired
    protected CouponIssuedRepository couponIssuedRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected OrderItemRepository orderItemRepository;

    @Autowired
    protected OrderItemCouponRepository orderItemCouponRepository;

    @Autowired
    protected IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanBeforeEach() {
        cleanDatabase();
    }

    protected OrderFixture createOrderFixtureWithCoupon(int inventoryQuantity, int cartQuantity) {
        return createOrderFixture(inventoryQuantity, cartQuantity, true);
    }

    protected OrderFixture createOrderFixtureWithoutCoupon(int inventoryQuantity, int cartQuantity) {
        return createOrderFixture(inventoryQuantity, cartQuantity, false);
    }

    private OrderFixture createOrderFixture(int inventoryQuantity, int cartQuantity, boolean withCoupon) {
        return tx(() -> {
            String suffix = Long.toString(TEST_DATA_SEQUENCE.incrementAndGet());

            User buyer = userRepository.save(User.join(
                    "buyer-" + suffix + "@e.com",
                    "buyer",
                    "password",
                    UserRole.BUYER
            ));
            User seller = userRepository.save(User.join(
                    "seller-" + suffix + "@e.com",
                    "seller",
                    "password",
                    UserRole.SELLER
            ));

            ProductCategory category = productCategoryRepository.save(new ProductCategory("category-" + suffix));
            Product product = productRepository.save(Product.register(
                    "product-" + suffix,
                    category,
                    "테스트 상품입니다.",
                    10_000L,
                    seller
            ));
            productStatRepository.save(new ProductStat(product));
            Inventory inventory = inventoryRepository.save(new Inventory(product, inventoryQuantity));
            CartItem cartItem = cartItemRepository.save(new CartItem(buyer, product, cartQuantity));

            Long couponIssuedId = null;
            if (withCoupon) {
                CouponEvent couponEvent = couponEventRepository.save(new CouponEvent(
                        "coupon-" + suffix,
                        CouponType.FIXED_AMOUNT,
                        1_000L,
                        1_000L,
                        100,
                        Instant.now().minusSeconds(60),
                        Instant.now().plusSeconds(3_600),
                        3_600
                ));
                CouponIssued couponIssued = couponIssuedRepository.save(
                        new CouponIssued(couponEvent, buyer, Instant.now())
                );
                couponIssuedId = couponIssued.getId();
            }

            return new OrderFixture(
                    buyer.getId(),
                    seller.getId(),
                    product.getId(),
                    inventory.getId(),
                    cartItem.getId(),
                    couponIssuedId
            );
        });
    }

    protected OrderReq orderReq(OrderFixture fixture, int orderQuantity) {
        return orderReq(fixture.cartItemId(), orderQuantity, fixture.couponIssuedId());
    }

    protected OrderReq orderReq(Long cartItemId, int orderQuantity, Long couponIssuedId) {
        return new OrderReq(List.of(new OrderReq.OrderItemReq(cartItemId, orderQuantity, couponIssuedId)));
    }

    protected OrderItem getOnlyOrderItem(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);

        assertThat(orderItems)
                .as("orderId=%s 주문항목은 정확히 1개여야 합니다.", orderId)
                .hasSize(1);

        return orderItems.get(0);
    }

    protected int inventoryQuantity(Long productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    protected long productOrderItemCount(Long productId) {
        return productStatRepository.findById(productId).orElseThrow().getOrderItemCount();
    }

    protected Integer cartQuantity(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .map(CartItem::getQuantity)
                .orElse(null);
    }

    protected CouponStatus couponStatus(Long couponIssuedId) {
        return couponIssuedRepository.findById(couponIssuedId).orElseThrow().getStatus();
    }

    protected OrderStatus orderItemStatus(Long orderItemId) {
        return orderItemRepository.findById(orderItemId).orElseThrow().getStatus();
    }

    protected <T> T tx(Supplier<T> supplier) {
        return new TransactionTemplate(transactionManager).execute(status -> supplier.get());
    }

    protected void tx(Runnable runnable) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> runnable.run());
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("set foreign_key_checks = 0");
        try {
            for (String table : CLEANUP_TABLES) {
                jdbcTemplate.update("delete from " + table);
            }
        } finally {
            jdbcTemplate.execute("set foreign_key_checks = 1");
        }
    }

    protected record OrderFixture(
            Long buyerId,
            Long sellerId,
            Long productId,
            Long inventoryId,
            Long cartItemId,
            Long couponIssuedId
    ) {
    }
}
