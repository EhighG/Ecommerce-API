package com.ecommerce.api.product.repository;

import com.ecommerce.api.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Slf4j
@RequiredArgsConstructor
@Repository
public class ProductStatJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public void increaseOrderCounts(List<IncreaseOrderCountCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        List<IncreaseOrderCountCommand> sortedCommands = commands.stream()
                .sorted(Comparator.comparingLong(IncreaseOrderCountCommand::productId))
                .toList();
        List<Object[]> batchArgs = sortedCommands.stream()
                .map(cmd -> new Object[]{
                        cmd.delta(),
                        cmd.productId()
                })
                .toList();

        int[] counts = jdbcTemplate.batchUpdate("""
                update product_stat
                set order_item_count = order_item_count + ?
                where product_id = ?
                """, batchArgs);

        for (int i = 0; i < counts.length; i++) {
            if (counts[i] != 1) {
                log.error("주문 수 증가가 1행이 아닙니다. productId = {}, updatedCount = {}",
                        sortedCommands.get(i).productId(), counts[i]);
                throw new AppException(PRODUCT_STAT_NOT_FOUND);
            }
        }
    }

    public void increaseViewCounts(List<IncreaseViewCountCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = commands.stream()
                .sorted(Comparator.comparingLong(IncreaseViewCountCommand::productId))
                .map(cmd -> new Object[]{
                        cmd.delta(),
                        cmd.productId()
                })
                .toList();

        int[] counts = jdbcTemplate.batchUpdate("""
                update product_stat
                set view_count = view_count + ?
                where product_id = ?
                """, batchArgs);

        for (int count : counts) {
            if (count != 1) {
                throw new AppException(PRODUCT_STAT_NOT_FOUND);
            }
        }
    }

    public record IncreaseOrderCountCommand(long productId, long delta) {}

    public record IncreaseViewCountCommand(long productId, long delta) {}
}
