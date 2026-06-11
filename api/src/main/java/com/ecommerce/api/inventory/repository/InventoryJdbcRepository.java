package com.ecommerce.api.inventory.repository;

import com.ecommerce.api.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Repository
public class InventoryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public void deductAll(List<DeductInventoryCommand> commands, Instant now) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        Timestamp updatedAt = Timestamp.from(now);

        List<Object[]> batchArgs = commands.stream()
                .sorted(Comparator.comparingLong(DeductInventoryCommand::productId)) // 락 획득 순서 통일
                .map(cmd -> new Object[]{
                        cmd.quantity(),
                        updatedAt,
                        cmd.productId(),
                        cmd.quantity()
                })
                .toList();

        int[] counts = jdbcTemplate.batchUpdate("""
                update inventory
                set quantity = quantity - ?,
                    updated_at = ?
                where product_id = ?
                and quantity >= ?
                """, batchArgs);

        for (int count : counts) {
            if (count != 1) { // SUCCESS_NO_INFO 응답 고려? 찾아보기
                throw new AppException(INSUFFICIENT_INVENTORY);
            }
        }
    }

    public record DeductInventoryCommand(long productId, int quantity) {}
}
