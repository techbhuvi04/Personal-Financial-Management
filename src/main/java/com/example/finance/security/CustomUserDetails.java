package com.example.finance.security;

import com.example.finance.entity.User;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal stored in the HTTP session. Carries the user id so every service call can be scoped to
 * the owner without trusting client input. The password hash is erased after authentication.
 */
public final class CustomUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final Long id;
    private final String username;
    private String passwordHash;

    /**
     * @param id           user id
     * @param username     canonical email
     * @param passwordHash encoded password
     */
    public CustomUserDetails(Long id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /**
     * @param user persisted user
     * @return principal for the user
     */
    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(user.getId(), user.getUsername(), user.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String toString() {
        return "CustomUserDetails[id=" + id + ", username=" + username + "]";
    }
}
