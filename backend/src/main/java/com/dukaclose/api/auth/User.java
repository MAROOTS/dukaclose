// User.java
package com.dukaclose.api.auth;

import com.dukaclose.api.common.BaseEntity;
import com.dukaclose.api.common.RecordStatus;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 40)
    private String loginIdentifier;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected User() {}   // required by JPA

    public User(String displayName, String loginIdentifier, String passwordHash) {
        this.displayName = displayName;
        this.loginIdentifier = loginIdentifier;
        this.passwordHash = passwordHash;
    }
    public void deactivate() {
        this.status = RecordStatus.INACTIVE;
    }

}