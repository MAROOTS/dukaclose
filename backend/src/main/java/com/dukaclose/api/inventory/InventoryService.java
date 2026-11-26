// InventoryService.java
package com.dukaclose.api.inventory;

import com.dukaclose.api.audit.AuditKind;
import com.dukaclose.api.audit.ChangeLogService;
import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Role;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.RecordStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final AccessService access;
    private final ItemRepository items;
    private final ShopInventoryRepository inventory;
    private final ChangeLogService changeLog;

    /** Owners and assigned cashiers may read. Only owners see cost. */
    @Transactional(readOnly = true)
    public List<InventoryRow> list(UUID userId, UUID shopId) {
        AccessService.ShopContext ctx = access.requireShopAccess(userId, shopId);
        boolean owner = ctx.role() == Role.OWNER;

        Map<UUID, ShopInventory> rows = inventory.findByShopId(shopId).stream()
                .collect(Collectors.toMap(ShopInventory::getItemId, Function.identity()));

        return items.findByBusinessIdAndStatusOrderByNameAsc(ctx.shop().getBusinessId(), RecordStatus.ACTIVE)
                .stream()
                .map(item -> InventoryRow.of(item, rows.get(item.getId()), owner))
                .toList();
    }

    @Transactional
    public InventoryRow update(UUID ownerId, UUID shopId, UUID itemId, UpdateShopItemRequest request) {
        Shop shop = access.requireShopOwner(ownerId, shopId).shop();

        Item item = items.findByIdAndBusinessId(itemId, shop.getBusinessId())
                .filter(i -> i.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "No such item in this business"));

        if (!item.getUnit().fitsScale(request.reorderLevel())) {
            throw ApiException.badRequest("INVALID_QUANTITY", "That reorder level has too many decimals for " + item.getUnit());
        }

        ShopInventory row = inventory.findForUpdate(shopId, itemId)
                .orElseGet(() -> new ShopInventory(shopId, shop.getBusinessId(), itemId, null));
        Long oldPrice = row.getPriceMinor();
        BigDecimal oldReorder = row.getReorderLevel();
        row.update(request.priceMinor(), request.reorderLevel());
        inventory.save(row);

        // a save that changes nothing is not an event
        if (!Objects.equals(oldPrice, request.priceMinor())) {
            changeLog.record(shop.getBusinessId(), shopId, AuditKind.PRICE_CHANGED, "SHOP_ITEM", itemId, ownerId,
                    ChangeLogService.map("item", item.getName(), "priceMinor", oldPrice),
                    ChangeLogService.map("item", item.getName(), "priceMinor", request.priceMinor()));
        }
        if (oldReorder.compareTo(request.reorderLevel()) != 0) {
            changeLog.record(shop.getBusinessId(), shopId, AuditKind.REORDER_LEVEL_CHANGED, "SHOP_ITEM", itemId, ownerId,
                    ChangeLogService.map("item", item.getName(), "reorderLevel", oldReorder),
                    ChangeLogService.map("item", item.getName(), "reorderLevel", request.reorderLevel()));
        }
        return InventoryRow.of(item, row, true);
    }

}