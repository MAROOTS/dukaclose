package com.dukaclose.api.inventory;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.business.ShopRepository;
import com.dukaclose.api.common.ApiException;
import com.dukaclose.api.common.RecordStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final AccessService access;
    private final ItemRepository items;
    private final ShopRepository shops;
    private final ShopInventoryRepository inventory;

    private record Pack(String name, BigDecimal size) {}

    @Transactional
    public ItemResponse create(UUID ownerId, UUID businessId, CreateItemRequest request) {
        access.requireBusinessOwner(ownerId, businessId);

        String name = request.name().trim();
        if (items.findByBusinessIdAndNameIgnoreCase(businessId, name).isPresent()) {
            throw ApiException.conflict("ITEM_NAME_TAKEN", "You already have an item with that name");
        }
        Pack pack = validatePack(request.unit(), request.packName(), request.packSize());

        Item item = items.save(new Item(businessId, name, request.unit(), pack.name(), pack.size()));

        // Optional: list the item, at one price, in every active shop of the business
        if (request.initialPriceMinor() != null) {
            for (Shop shop : shops.findByBusinessId(businessId)) {
                if (shop.getStatus() == RecordStatus.ACTIVE) {
                    inventory.save(new ShopInventory(shop.getId(), businessId, item.getId(), request.initialPriceMinor()));
                }
            }
        }
        return ItemResponse.of(item);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> list(UUID ownerId, UUID businessId) {
        access.requireBusinessOwner(ownerId, businessId);
        return items.findByBusinessIdOrderByNameAsc(businessId).stream().map(ItemResponse::of).toList();
    }

    @Transactional
    public ItemResponse update(UUID ownerId, UUID businessId, UUID itemId, UpdateItemRequest request) {
        access.requireBusinessOwner(ownerId, businessId);
        Item item = items.findByIdAndBusinessId(itemId, businessId)
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "No such item"));

        String name = request.name().trim();
        items.findByBusinessIdAndNameIgnoreCase(businessId, name)
                .filter(other -> !other.getId().equals(itemId))
                .ifPresent(other -> {
                    throw ApiException.conflict("ITEM_NAME_TAKEN", "You already have an item with that name");
                });

        Pack pack = validatePack(item.getUnit(), request.packName(), request.packSize());

        boolean archiving = !request.active() && item.getStatus() == RecordStatus.ACTIVE;
        if (archiving && inventory.existsByItemIdAndOnHandGreaterThan(itemId, BigDecimal.ZERO)) {
            throw ApiException.conflict("ITEM_HAS_STOCK", "This item still has stock in a shop. Clear the stock first.");
        }

        item.update(name, pack.name(), pack.size(), request.active());
        return ItemResponse.of(item);
    }

    private Pack validatePack(Unit unit, String packName, BigDecimal packSize) {
        String name = packName == null ? "" : packName.trim();
        if (name.isEmpty() && packSize == null) {
            return new Pack(null, null);
        }
        if (name.isEmpty() || packSize == null) {
            throw ApiException.badRequest("INVALID_PACK", "Give both a pack name and a pack size, or leave both empty");
        }
        if (!unit.fitsScale(packSize)) {
            throw ApiException.badRequest("INVALID_PACK", "That pack size has too many decimals for " + unit);
        }
        return new Pack(name, packSize);
    }
}