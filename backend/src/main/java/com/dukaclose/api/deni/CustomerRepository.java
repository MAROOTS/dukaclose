// CustomerRepository.java
package com.dukaclose.api.deni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByBusinessIdAndPhone(UUID businessId, String phone);
    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("select c from Customer c where c.businessId = :businessId and lower(c.name) like :name order by c.name")
    List<Customer> searchByName(@Param("businessId") UUID businessId, @Param("name") String name, Pageable page);

    @Query("select c from Customer c where c.businessId = :businessId "
            + "and (lower(c.name) like :name or c.phone like :phone) order by c.name")
    List<Customer> searchByNameOrPhone(@Param("businessId") UUID businessId, @Param("name") String name,
                                       @Param("phone") String phone, Pageable page);
}