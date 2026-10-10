package com.example.finance.service;

import com.example.finance.dto.auth.LoginRequest;
import com.example.finance.dto.auth.RegisterRequest;
import com.example.finance.entity.User;
import com.example.finance.exception.DuplicateResourceException;
import com.example.finance.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account registration and credential verification. Session handling is left to the web layer.
 */
@Service
public class AuthService {

    static final String DUPLICATE_USERNAME_MESSAGE = "An account with this username already exists";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    /**
     * @param userRepository        user persistence
     * @param passwordEncoder       password hashing
     * @param authenticationManager credential verification
     */
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Creates an account with a BCrypt-hashed password.
     *
     * @param request validated registration data (username already normalized)
     * @return id of the new user
     * @throws DuplicateResourceException if the username is taken, including by a concurrent registration
     */
    @Transactional
    public Long register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException(DUPLICATE_USERNAME_MESSAGE);
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()),
                request.fullName(), request.phoneNumber());
        try {
            return userRepository.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException ex) {
            // A concurrent registration won the race for the unique username constraint.
            throw new DuplicateResourceException(DUPLICATE_USERNAME_MESSAGE);
        }
    }

    /**
     * Verifies credentials. Unknown usernames and wrong passwords fail identically.
     *
     * @param request login data (username already normalized)
     * @return the successful authentication
     * @throws org.springframework.security.core.AuthenticationException if the credentials are invalid
     */
    public Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
    }
}
