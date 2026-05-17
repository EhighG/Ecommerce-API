package com.ecommerce.api.inventory.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.inventory.dto.ModifyInventoryReq;
import com.ecommerce.api.inventory.entity.Inventory;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

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

        // update 순서 통일(데드락 방지)을 위해 정렬
        List<Long> productIds = quantityByProductId.keySet().stream()
                .sorted()
                .toList();

        Map<Long, Inventory> inventoryByProductId = inventoryRepository.findAllByProductIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(
                        inventory -> inventory.getProduct().getId(),
                        Function.identity()
                ));

        if (inventoryByProductId.size() != productIds.size()) {
            throw new AppException(NO_INVENTORY_FOR_PRODUCT);
        }

        // 재고 부족을 앱 단에서 미리 거를 수 있으면, 거름
        for (Long productId : productIds) {
            int orderQuantity = quantityByProductId.get(productId);
            Inventory inventory = inventoryByProductId.get(productId);

            if (inventory.getQuantity() < orderQuantity) {
                throw new AppException(INSUFFICIENT_INVENTORY);
            }
        }

        for (Long productId : productIds) {
            int orderQuantity = quantityByProductId.get(productId);
            int updatedCount = inventoryRepository.deductIfEnoughQuantity(productId, orderQuantity, Instant.now());

            if (updatedCount != 1) {
                throw new AppException(INSUFFICIENT_INVENTORY);
            }
        }
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
