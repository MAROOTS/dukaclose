package com.dukaclose.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import java.util.UUID;

import com.dukaclose.api.business.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FoundationSchemaTest {

    @Autowired
    BusinessRepository businesses;
    @Autowired
    ShopRepository shops;
    @Autowired UserRepository users;
    @Autowired
    BusinessMembershipRepository businessMemberships;
    @Autowired
    ShopMembershipRepository shopMemberships;

    @Test
    void ownerAndCashierAreLinkedThroughMemberships() {
        Business business = businesses.save(new Business("Mama Njeri Stores"));
        Shop shop = shops.save(new Shop(business.getId(), "Kikuyu Branch"));
        User owner = users.save(new User("Njeri", "+254700000001", "hash"));
        User cashier = users.save(new User("Otieno", "+254700000002", "hash"));

        businessMemberships.save(new BusinessMembership(business.getId(), owner.getId(), Role.OWNER));
        shopMemberships.save(new ShopMembership(shop.getId(), cashier.getId(), Role.CASHIER));

        assertThat(businessMemberships.findByUserId(owner.getId())).hasSize(1);
        assertThat(businessMemberships.findByUserId(cashier.getId())).isEmpty();
        assertThat(shopMemberships.findByUserId(cashier.getId())).hasSize(1);
    }

    @Test
    void loginIdentifierMustBeUnique() {
        users.saveAndFlush(new User("A", "+254700000001", "hash"));

        assertThatThrownBy(() -> users.saveAndFlush(new User("B", "+254700000001", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shopNamesAreUniqueWithinABusinessOnly() {
        Business a = businesses.save(new Business("A Stores"));
        Business b = businesses.save(new Business("B Stores"));
        shops.saveAndFlush(new Shop(a.getId(), "Main"));
        shops.saveAndFlush(new Shop(b.getId(), "Main"));   // different business: allowed

        assertThatThrownBy(() -> shops.saveAndFlush(new Shop(a.getId(), "Main")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shopMustBelongToARealBusiness() {
        assertThatThrownBy(() -> shops.saveAndFlush(new Shop(UUID.randomUUID(), "Ghost")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userCannotBeAssignedToTheSameShopTwice() {
        Business business = businesses.save(new Business("Mama Njeri Stores"));
        Shop shop = shops.save(new Shop(business.getId(), "Main"));
        User cashier = users.save(new User("Otieno", "+254700000002", "hash"));
        shopMemberships.saveAndFlush(new ShopMembership(shop.getId(), cashier.getId(), Role.CASHIER));

        assertThatThrownBy(() ->
                shopMemberships.saveAndFlush(new ShopMembership(shop.getId(), cashier.getId(), Role.CASHIER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}