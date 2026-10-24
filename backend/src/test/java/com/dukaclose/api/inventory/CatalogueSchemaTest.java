package com.dukaclose.api.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dukaclose.api.business.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogueSchemaTest {

    @Autowired BusinessRepository businesses;
    @Autowired ShopRepository shops;
    @Autowired ItemRepository items;
    @Autowired ShopInventoryRepository inventory;

    @Test
    void itemNamesAreUniqueWithinABusinessIgnoringCase() {
        Business a = businesses.save(new Business("A"));
        Business b = businesses.save(new Business("B"));
        items.saveAndFlush(new Item(a.getId(), "Sugar", Unit.KG, null, null));
        items.saveAndFlush(new Item(b.getId(), "Sugar", Unit.KG, null, null));   // other business: allowed

        assertThatThrownBy(() -> items.saveAndFlush(new Item(a.getId(), "SUGAR", Unit.KG, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aPackNeedsBothANameAndASize() {
        Business a = businesses.save(new Business("A"));

        assertThatThrownBy(() -> items.saveAndFlush(new Item(a.getId(), "Sugar", Unit.KG, "Sack", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void inventoryCannotLinkAnItemFromAnotherBusiness() {
        Business a = businesses.save(new Business("A"));
        Business b = businesses.save(new Business("B"));
        Shop shopA = shops.save(new Shop(a.getId(), "A Main"));
        Item itemB = items.save(new Item(b.getId(), "Flour", Unit.KG, null, null));

        assertThatThrownBy(() -> inventory.saveAndFlush(
                new ShopInventory(shopA.getId(), a.getId(), itemB.getId(), 10000L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void inventoryCannotLinkAShopFromAnotherBusiness() {
        Business a = businesses.save(new Business("A"));
        Business b = businesses.save(new Business("B"));
        Shop shopA = shops.save(new Shop(a.getId(), "A Main"));
        Item itemB = items.save(new Item(b.getId(), "Flour", Unit.KG, null, null));

        // business_id says B, but the shop belongs to A
        assertThatThrownBy(() -> inventory.saveAndFlush(
                new ShopInventory(shopA.getId(), b.getId(), itemB.getId(), 10000L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aShopCanListAnItemOnlyOnce() {
        Business a = businesses.save(new Business("A"));
        Shop shop = shops.save(new Shop(a.getId(), "Main"));
        Item item = items.save(new Item(a.getId(), "Rice", Unit.KG, null, null));
        inventory.saveAndFlush(new ShopInventory(shop.getId(), a.getId(), item.getId(), 15000L));

        assertThatThrownBy(() -> inventory.saveAndFlush(
                new ShopInventory(shop.getId(), a.getId(), item.getId(), 16000L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}