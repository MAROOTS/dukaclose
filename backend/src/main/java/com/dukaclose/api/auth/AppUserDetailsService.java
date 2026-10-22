// AppUserDetailsService.java
package com.dukaclose.api.auth;

import com.dukaclose.api.common.RecordStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String loginIdentifier) {
        User user = users.findByLoginIdentifier(loginIdentifier)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new AppUserDetails(
                user.getId(),
                user.getDisplayName(),
                user.getLoginIdentifier(),
                user.getPasswordHash(),
                user.getStatus() == RecordStatus.ACTIVE);
    }
}