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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
    protected JdbcTemplate jdbcTemplate;

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

    /**
     * 지정한 테이블에서 락을 기다리는 트랜잭션이 정확히 expectedWaiters개가 될 때까지 기다린다.
     * MySQL이 보고하는 락 대기 상태({@code performance_schema.data_lock_waits})로 단계 순서를 맞추기 위한 것이다.
     * 제한 시간 안에 그 상태가 되지 않거나 더 많이 기다리면 테스트를 실패시킨다.
     */
    protected void awaitLockWaiters(String tableName, int expectedWaiters, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        int waiters;

        try (Connection root = openRootConnection();
             PreparedStatement statement = root.prepareStatement("""
                     select count(distinct w.REQUESTING_ENGINE_TRANSACTION_ID)
                     from performance_schema.data_lock_waits w
                     join performance_schema.data_locks l
                       on l.ENGINE = w.ENGINE
                      and l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
                     where l.OBJECT_SCHEMA = ?
                       and l.OBJECT_NAME = ?
                     """)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, tableName);

            while (true) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    waiters = resultSet.getInt(1);
                }

                if (waiters > expectedWaiters) {
                    fail("%s 테이블의 락 대기가 예상(%d)보다 많습니다. 실제 = %d", tableName, expectedWaiters, waiters);
                }
                if (waiters == expectedWaiters) {
                    return;
                }
                if (System.nanoTime() >= deadline) {
                    fail("%s 테이블에서 락 대기 %d개가 %s 안에 생기지 않았습니다. 마지막으로 본 대기 수 = %d",
                            tableName, expectedWaiters, timeout, waiters);
                }

                LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("락 대기 상태를 조회하지 못했습니다.", e);
        }
    }

    /**
     * 테스트가 직접 락을 잡거나 다른 트랜잭션 역할을 할 때 쓰는 root 연결이다. 자동 커밋은 꺼져 있다.
     * 호출한 쪽이 반드시 닫아야 한다. 커밋하지 않고 닫으면 변경이 롤백된다.
     */
    protected Connection openRootConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
        connection.setAutoCommit(false);
        return connection;
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
