-- 현재 스키마 전체 DDL (MySQL 8.4)
-- 엔티티 매핑에서 만든 테이블에, 엔티티에 선언되지 않은 FULLTEXT 인덱스(product.ft_product_name_description)를 더했다.
-- 엔티티 매핑이나 DB 객체를 바꾸면 이 파일도 같은 변경으로 고친다.
--
-- 사용: 빈 DB(예: ecommerce)에 한 번 실행한다. 이미 운영 중인 DB에는 실행하지 않고, 변경분 DDL만 따로 적용한다.
--
-- 필요한 MySQL 서버 옵션(로컬과 클라우드 모두. 서버를 띄울 때만 정할 수 있다):
--   --ngram_token_size=2 --innodb_ft_enable_stopword=OFF
-- 옵션 확인(2, OFF가 나와야 한다):
--   SHOW VARIABLES WHERE Variable_name IN ('ngram_token_size', 'innodb_ft_enable_stopword');
-- 인덱스 확인(access type이 fulltext로 나와야 한다):
--   EXPLAIN SELECT id FROM product WHERE MATCH(name, description) AGAINST('+반팔' IN BOOLEAN MODE);

-- product와 product_image가 서로를 참조하므로 FK 검사를 잠시 끈다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `cart_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int NOT NULL,
  `product_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_item_user_product` (`user_id`,`product_id`),
  KEY `FKjcyd5wv4igqnw413rgxbfu4nv` (`product_id`),
  CONSTRAINT `FKjcyd5wv4igqnw413rgxbfu4nv` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `FKka3t831w0aw2vrwgsbhcn5y4m` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `cart_item_chk_1` CHECK ((`quantity` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `initial_quantity` int NOT NULL,
  `discount_value` bigint NOT NULL,
  `end_at` datetime(6) NOT NULL,
  `max_discount_amount` bigint NOT NULL,
  `start_at` datetime(6) NOT NULL,
  `valid_seconds` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `type` enum('FIXED_AMOUNT','PERCENT') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_issued` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `coupon_event_id` bigint NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `issued_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `status` enum('EXPIRED','ISSUED','USED') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_issued_event_user` (`coupon_event_id`,`user_id`),
  KEY `FKhq9wec237wiml23xiaih4flaw` (`user_id`),
  CONSTRAINT `FK3pwf6mktdy88ntnqkjb825845` FOREIGN KEY (`coupon_event_id`) REFERENCES `coupon_event` (`id`),
  CONSTRAINT `FKhq9wec237wiml23xiaih4flaw` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `idempotency_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expires_at` datetime(6) NOT NULL,
  `resource_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `request_fingerprint` varchar(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `resource_type` enum('ORDER') DEFAULT NULL,
  `scope` enum('ORDER_CREATE') NOT NULL,
  `status` enum('PROCESSING','SUCCEEDED') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_record_user_scope_key` (`user_id`,`scope`,`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int NOT NULL,
  `product_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inventory_product` (`product_id`),
  CONSTRAINT `FKp7gj4l80fx8v0uap3b2crjwp5` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `inventory_chk_1` CHECK ((`quantity` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int NOT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `line_price` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `product_unit_price` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `product_description` varchar(1000) NOT NULL,
  `product_category_name` varchar(255) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `seller_nickname` varchar(255) NOT NULL,
  `thumbnail_path` varchar(255) DEFAULT NULL,
  `status` enum('CANCELED','DELIVERED','ORDERED','PURCHASE_CONFIRMED','SHIPPED') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKt4dc2r9nbvbujrljv3e23iibt` (`order_id`),
  CONSTRAINT `FKt4dc2r9nbvbujrljv3e23iibt` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_item_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `coupon_discount_value` bigint NOT NULL,
  `coupon_event_id` bigint NOT NULL,
  `coupon_issued_id` bigint NOT NULL,
  `coupon_max_discount_amount` bigint NOT NULL,
  `discounted_amount` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `used_at` datetime(6) NOT NULL,
  `coupon_name` varchar(100) NOT NULL,
  `coupon_type` enum('FIXED_AMOUNT','PERCENT') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item_coupon_order_item` (`order_item_id`),
  CONSTRAINT `FKiuj2ks6mqp9ssdimxr7if6r2h` FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `buyer_id` bigint NOT NULL,
  `ordered_at` datetime(6) DEFAULT NULL,
  `total_price` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhtx3insd5ge6w486omk4fnk54` (`buyer_id`),
  CONSTRAINT `FKhtx3insd5ge6w486omk4fnk54` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `deleted` bit(1) NOT NULL,
  `product_category_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `thumbnail_image_id` bigint DEFAULT NULL,
  `unit_price` bigint NOT NULL,
  `name` varchar(50) NOT NULL,
  `description` varchar(1000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKq7ho6ugqk6ifti809u48w9e2q` (`thumbnail_image_id`),
  KEY `idx_product_deleted_created_id` (`deleted`,`created_at` DESC,`id` DESC),
  KEY `idx_product_category_deleted_created_id` (`product_category_id`,`deleted`,`created_at` DESC,`id` DESC),
  KEY `FKnuvtfgcf3ohskgoyi6v1eh1jr` (`seller_id`),
  FULLTEXT KEY `ft_product_name_description` (`name`,`description`) WITH PARSER `ngram`,
  CONSTRAINT `FKbt60h384i19n0bw70uq2q2kp6` FOREIGN KEY (`thumbnail_image_id`) REFERENCES `product_image` (`id`),
  CONSTRAINT `FKcwclrqu392y86y0pmyrsi649r` FOREIGN KEY (`product_category_id`) REFERENCES `product_category` (`id`),
  CONSTRAINT `FKnuvtfgcf3ohskgoyi6v1eh1jr` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`),
  CONSTRAINT `product_chk_1` CHECK ((`unit_price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(30) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `display_order` int NOT NULL,
  `product_id` bigint NOT NULL,
  `uploaded_image_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_image_uploaded_image` (`uploaded_image_id`),
  UNIQUE KEY `uk_product_image_product_id_display_order` (`product_id`,`display_order`),
  CONSTRAINT `FK3y86diktu3hotisg0cg7cq9ps` FOREIGN KEY (`uploaded_image_id`) REFERENCES `uploaded_image` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_stat` (
  `product_id` bigint NOT NULL,
  `rating_avg` double NOT NULL,
  `order_item_count` bigint NOT NULL,
  `rating_sum` bigint NOT NULL,
  `review_count` bigint NOT NULL,
  `view_count` bigint NOT NULL,
  PRIMARY KEY (`product_id`),
  CONSTRAINT `FKqh6sdr4xheuujk6b0dqh3hgf0` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rating` int NOT NULL,
  `product_id` bigint NOT NULL,
  `writer_id` bigint NOT NULL,
  `content` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_writer_product` (`writer_id`,`product_id`),
  KEY `FKiyof1sindb9qiqr9o8npj8klt` (`product_id`),
  CONSTRAINT `FKiyof1sindb9qiqr9o8npj8klt` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `FKrcworsh1jf3un5ebdf11d0g22` FOREIGN KEY (`writer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `uploaded_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attached` bit(1) NOT NULL,
  `file_size` bigint NOT NULL,
  `upload_user_id` bigint NOT NULL,
  `content_type` varchar(255) NOT NULL,
  `object_key` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `deleted` bit(1) NOT NULL,
  `nickname` varchar(20) NOT NULL,
  `email` varchar(30) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ADMIN','BUYER','SELLER') DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
