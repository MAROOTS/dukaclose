// dev/DevDataSeeder.java
package com.dukaclose.api.dev;

import com.dukaclose.api.auth.User;
import com.dukaclose.api.auth.UserRepository;
import com.dukaclose.api.business.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements ApplicationRunner {

    private static final String OWNER_PHONE = "+254700000001";

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final ShopRepository shops;
    private final BusinessMembershipRepository businessMemberships;
    private final ShopMembershipRepository shopMemberships;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByLoginIdentifier(OWNER_PHONE).isPresent()) {
            log.info("Dev seed data already present, skipping");
            return;
        }

        Business business = businesses.save(new Business("Mama Njeri Stores"));
        Shop kikuyu = shops.save(new Shop(business.getId(), "Kikuyu Branch"));
        Shop limuru = shops.save(new Shop(business.getId(), "Limuru Branch"));

        User owner = users.save(new User("Njeri (Owner)", OWNER_PHONE, passwordEncoder.encode("owner123")));
        User otieno = users.save(new User("Otieno (Cashier)", "+254700000002", passwordEncoder.encode("cashier123")));
        User wanjiru = users.save(new User("Wanjiru (Cashier)", "+254700000003", passwordEncoder.encode("cashier123")));

        businessMemberships.save(new BusinessMembership(business.getId(), owner.getId(), Role.OWNER));
        shopMemberships.save(new ShopMembership(kikuyu.getId(), otieno.getId(), Role.CASHIER));
        shopMemberships.save(new ShopMembership(limuru.getId(), wanjiru.getId(), Role.CASHIER));

        log.info("Dev seed created: owner +254700000001/owner123, cashiers +254700000002 (Kikuyu) and +254700000003 (Limuru) / cashier123");
    }
}