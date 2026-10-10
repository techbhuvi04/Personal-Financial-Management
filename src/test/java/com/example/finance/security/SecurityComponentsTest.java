package com.example.finance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.finance.entity.User;
import com.example.finance.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityComponentsTest {

    private final SecurityErrorHandler errorHandler =
            new SecurityErrorHandler(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void entryPointWritesJson401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        errorHandler.commence(new MockHttpServletRequest("GET", "/api/goals"), response,
                new InsufficientAuthenticationException("no session"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"path\":\"/api/goals\"").contains("\"status\":401");
    }

    @Test
    void accessDeniedHandlerDistinguishesCsrfFailures() throws Exception {
        MockHttpServletResponse csrf = new MockHttpServletResponse();
        MockHttpServletResponse generic = new MockHttpServletResponse();

        errorHandler.handle(new MockHttpServletRequest(), csrf,
                new InvalidCsrfTokenException(new DefaultCsrfToken("X", "_csrf", "a"), "b"));
        errorHandler.handle(new MockHttpServletRequest(), generic, new AccessDeniedException("nope"));

        assertThat(csrf.getStatus()).isEqualTo(403);
        assertThat(csrf.getContentAsString()).contains("Missing or invalid CSRF token");
        assertThat(generic.getContentAsString()).contains("Access denied");
    }

    @Test
    void userDetailsServiceNormalizesUsernameAndHidesMissingUsers() {
        UserRepository repository = mock(UserRepository.class);
        User user = new User("a@b.com", "hash", "A", "+1234567890");
        ReflectionTestUtils.setField(user, "id", 3L);
        when(repository.findByUsername("a@b.com")).thenReturn(Optional.of(user));
        when(repository.findByUsername("x@y.com")).thenReturn(Optional.empty());
        CustomUserDetailsService service = new CustomUserDetailsService(repository);

        CustomUserDetails details = (CustomUserDetails) service.loadUserByUsername(" A@B.com ");

        assertThat(details.getId()).isEqualTo(3L);
        assertThat(details.getUsername()).isEqualTo("a@b.com");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThatThrownBy(() -> service.loadUserByUsername("x@y.com")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void principalErasesCredentialsAndNeverPrintsThem() {
        CustomUserDetails details = new CustomUserDetails(1L, "a@b.com", "hash");

        details.eraseCredentials();

        assertThat(details.getPassword()).isNull();
        assertThat(details.toString()).doesNotContain("hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }
}
