package com.ecommerce.api.inventory.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.inventory.dto.ModifyInventoryReq;
import com.ecommerce.api.inventory.entity.Inventory;
import com.ecommerce.api.inventory.repository.InventoryJdbcRepository;
import com.ecommerce.api.inventory.repository.InventoryJdbcRepository.DeductInventoryCommand;
import com.ecommerce.api.inventory.repository.InventoryRepository;
import com.ecommerce.api.order.vo.OrderLine;
import com.ecommerce.api.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryJdbcRepository inventoryJdbcRepository;

    @Transactional
    public void createInventory(Product product, int quantity) {
        inventoryRepository.save(new Inventory(product, quantity));
    }

    public Inventory getInventoryByProductId(Long productId) {
        // 현재 요구사항(=서비스 로직)상, 재고정보 없으면 서버 오류인 상황(DB or WAS)
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new AppException(NO_INVENTORY_FOR_PRODUCT));
    }

    @Transactional
    public void validateAndDeduct(List<OrderLine> orderLines) {
        Map<Long, Integer> quantityByProductId = new HashMap<>();
        for (OrderLine orderLine : orderLines) {
            quantityByProductId.merge(orderLine.product().getId(), orderLine.quantity(), Integer::sum);
        }

        List<DeductInventoryCommand> commands =
                quantityByProductId.entrySet().stream()
                        .map(entry -> new DeductInventoryCommand(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .toList();

        inventoryJdbcRepository.deductAll(commands, Instant.now());
    }

    @Transactional
    public void restore(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                        .orElseThrow(() -> new AppException(NO_INVENTORY_FOR_PRODUCT));

        inventory.adjust(quantity);
    }

    @Transactional
    public void modifyInventory(ModifyInventoryReq req, Long userId) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(req.productId())
                .orElseThrow(() -> new AppException(NO_INVENTORY_FOR_PRODUCT));

        if (inventory.getProduct().isDeleted())
            throw new AppException(DELETED_PRODUCT);

        if (!inventory.getProduct().getSeller().getId().equals(userId))
            throw new AppException(SELLER_NOT_MATCHED);

        inventory.update(req.quantity());
    }
}
