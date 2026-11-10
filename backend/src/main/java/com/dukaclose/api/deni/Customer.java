package com.dukaclose.api.deni;

import com.dukaclose.api.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 20) private String phone;
    private Long creditLimitMinor;

    public Customer(UUID businessId, String name, String phone) {
        this.businessId = businessId;
        this.name = name;
        this.phone = phone;
    }

    public void changeCreditLimit(Long creditLimitMinor) {
        this.creditLimitMinor = creditLimitMinor;
    }
}