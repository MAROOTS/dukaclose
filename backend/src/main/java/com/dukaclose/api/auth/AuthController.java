// auth/AuthController.java
package com.dukaclose.api.auth;

import com.dukaclose.api.common.ApiError;
import com.dukaclose.api.common.PhoneNumbers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final MeService meService;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    @PostMapping("/auth/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(PhoneNumbers.lenient(body.identifier()), body.password()));

        // Protect against session fixation: new session id after a successful login
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);   // persist into the session

        return meService.me((AppUserDetails) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        return meService.me(principal);
    }

    // One generic message for wrong password, unknown phone and inactive account
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> badLogin(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_CREDENTIALS", "Invalid phone or password"));
    }
}