package com.example.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.finance.dto.auth.LoginRequest;
import com.example.finance.dto.auth.RegisterRequest;
import com.example.finance.entity.User;
import com.example.finance.exception.DuplicateResourceException;
import com.example.finance.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @InjectMocks
    private AuthService authService;

    private final RegisterRequest request =
            new RegisterRequest(" User@Example.com ", "password123", "John Doe", "+1234567890");

    @Test
    void registerHashesPasswordAndStoresCanonicalUsername() {
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 42L);
            return user;
        });

        assertThat(authService.register(request)).isEqualTo(42L);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("user@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getFullName()).isEqualTo("John Doe");
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo("+1234567890");
    }

    @Test
    void registerRejectsExistingUsernameWithoutHashing() {
        when(userRepository.existsByUsername("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage(AuthService.DUPLICATE_USERNAME_MESSAGE);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void registerTranslatesUniqueConstraintRaceIntoDuplicate() {
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void authenticateDelegatesWithNormalizedUsername() {
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated("u", null, null);
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);

        assertThat(authService.authenticate(new LoginRequest(" USER@example.com", "pw"))).isSameAs(authenticated);

        ArgumentCaptor<Authentication> token = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(token.capture());
        assertThat(token.getValue().getName()).isEqualTo("user@example.com");
    }

    @Test
    void authenticatePropagatesBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.authenticate(new LoginRequest("a@b.com", "pw")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
