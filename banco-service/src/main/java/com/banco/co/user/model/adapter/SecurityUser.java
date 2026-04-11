package com.banco.co.user.model.adapter;

import com.banco.co.user.model.UserCredential;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Getter
public class SecurityUser implements UserDetails {

    private final UserCredential user;

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles()
                .stream()
                .flatMap(role -> Stream.concat(
                        // Rol como "ROLE_TELLER"
                        Stream.of(new SimpleGrantedAuthority("ROLE_".concat(role.getName().getRoleName()))),

                        // Scopes como "account:read", "transaction:create"
                        role.getPermissions()
                                .stream()
                                .map(permission -> new SimpleGrantedAuthority(permission.getScope()))
                ))
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    @NonNull
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return user.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return user.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
