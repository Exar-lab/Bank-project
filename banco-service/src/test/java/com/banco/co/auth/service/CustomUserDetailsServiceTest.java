package com.banco.co.auth.service;

import com.banco.co.user.model.User;
import com.banco.co.user.model.UserCredential;
import com.banco.co.user.model.adapter.SecurityUser;
import com.banco.co.user.repository.IUserCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private IUserCredential userCredentialRepository;
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        userCredentialRepository = mock(IUserCredential.class);
        customUserDetailsService = new CustomUserDetailsService(userCredentialRepository);
    }

    @Test
    void testLoadUserByUsername_WhenUserExists_ReturnsSecurityUserWithSameLockState() {
        UserCredential credential = buildCredential("user@banco.co", true);
        when(userCredentialRepository.findByEmailWithRolesAndPermissions("user@banco.co"))
                .thenReturn(Optional.of(credential));

        UserDetails loaded = customUserDetailsService.loadUserByUsername("user@banco.co");

        assertThat(loaded).isInstanceOf(SecurityUser.class);
        assertThat(loaded.isAccountNonLocked()).isTrue();
        assertThat(loaded.getUsername()).isEqualTo("user@banco.co");
    }

    @Test
    void testLoadUserByUsername_WhenCredentialIsLocked_ReturnsLockedUserDetails() {
        UserCredential credential = buildCredential("locked@banco.co", false);
        when(userCredentialRepository.findByEmailWithRolesAndPermissions("locked@banco.co"))
                .thenReturn(Optional.of(credential));

        UserDetails loaded = customUserDetailsService.loadUserByUsername("locked@banco.co");

        assertThat(loaded.isAccountNonLocked()).isFalse();
    }

    @Test
    void testLoadUserByUsername_WhenUserMissing_ThrowsUsernameNotFoundException() {
        when(userCredentialRepository.findByEmailWithRolesAndPermissions("missing@banco.co"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("missing@banco.co"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing@banco.co");
    }

    @Test
    void testSecurityUser_IsAccountNonLocked_MatchesCredentialState() {
        UserCredential unlocked = buildCredential("u@banco.co", true);
        UserCredential locked = buildCredential("l@banco.co", false);

        assertThat(new SecurityUser(unlocked).isAccountNonLocked()).isTrue();
        assertThat(new SecurityUser(locked).isAccountNonLocked()).isFalse();
    }

    private UserCredential buildCredential(String email, boolean accountNonLocked) {
        User user = new User();
        user.setEmail(email);

        UserCredential credential = new UserCredential();
        credential.setId(UUID.randomUUID());
        credential.setEmail(email);
        credential.setPasswordHash("$2a$10$dummyhash");
        credential.setUser(user);
        credential.setRoles(new HashSet<>());
        credential.setAccountNonLocked(accountNonLocked);
        credential.setEnabled(true);
        credential.setAccountNonExpired(true);
        credential.setCredentialsNonExpired(true);
        return credential;
    }
}
