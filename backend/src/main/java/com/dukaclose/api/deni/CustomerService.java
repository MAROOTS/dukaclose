// CustomerService.java
package com.dukaclose.api.deni;

import com.dukaclose.api.business.AccessService;
import com.dukaclose.api.business.Shop;
import com.dukaclose.api.common.ApiException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final int MAX_RESULTS = 20;

    private final AccessService access;
    private final CustomerRepository customers;
    private final DebtService debts;
    private final JdbcTemplate jdbc;

    /**
     * The phone number IS the customer. An existing customer keeps the name already stored.
     * The insert ignores a clash, so two cashiers adding the same new customer at once cannot fail each other.
     */
    @Transactional
    public Customer findOrCreate(UUID businessId, String name, String phone) {
        jdbc.update("insert into customers (id, business_id, name, phone) values (gen_random_uuid(), ?, ?, ?) "
                + "on conflict (business_id, phone) do nothing", businessId, name, phone);
        return customers.findByBusinessIdAndPhone(businessId, phone).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Map<UUID, Customer> byIds(Collection<UUID> ids) {
        return customers.findAllById(ids).stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<CustomerView> search(UUID userId, UUID shopId, String query) {
        Shop shop = access.requireShopAccess(userId, shopId).shop();
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();

        String name = "%" + q.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        String digits = q.replaceAll("\\D", "");
        PageRequest page = PageRequest.of(0, MAX_RESULTS);

        List<Customer> found;
        if (digits.length() >= 3) {
            // stored as +2547..., typed as 07... or 2547... or just a few digits from the middle
            String fragment = digits.startsWith("0") ? "254" + digits.substring(1) : digits;
            found = customers.searchByNameOrPhone(shop.getBusinessId(), name, "%" + fragment + "%", page);
        } else {
            found = customers.searchByName(shop.getBusinessId(), name, page);
        }
        return views(found);
    }

    @Transactional
    public CustomerView setCreditLimit(UUID ownerId, UUID businessId, UUID customerId, Long limitMinor) {
        access.requireBusinessOwner(ownerId, businessId);
        Customer customer = customers.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "No such customer"));
        customer.changeCreditLimit(limitMinor);
        return views(List.of(customer)).get(0);
    }

    private List<CustomerView> views(List<Customer> list) {
        Map<UUID, Long> owed = debts.outstanding(list.stream().map(Customer::getId).toList());
        return list.stream().map(c -> {
            long outstanding = owed.getOrDefault(c.getId(), 0L);
            Long limit = c.getCreditLimitMinor();
            return new CustomerView(c.getId(), c.getName(), c.getPhone(), outstanding, limit,
                    limit != null && outstanding > limit);
        }).toList();
    }
}