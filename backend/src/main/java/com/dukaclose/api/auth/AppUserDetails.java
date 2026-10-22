// AppUserDetails.java
package com.dukaclose.api.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AppUserDetails(
        UUID userId,
        String displayName,
        String loginIdentifier,
        String passwordHash,
        boolean active) implements UserDetails {

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return loginIdentifier; }
    @Override public boolean isEnabled() { return active; }
}