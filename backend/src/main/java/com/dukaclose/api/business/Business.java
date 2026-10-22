// Business.java
package com.dukaclose.api.business;

import com.dukaclose.api.common.BaseEntity;
import com.dukaclose.api.common.RecordStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "businesses")
public class Business extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected Business() {}

    public Business(String name) { this.name = name; }

    public String getName() { return name; }
    public RecordStatus getStatus() { return status; }
}