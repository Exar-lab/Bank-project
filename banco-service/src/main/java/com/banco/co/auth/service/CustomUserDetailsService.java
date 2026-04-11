package com.banco.co.auth.service;

import com.banco.co.user.model.UserCredential;
import com.banco.co.user.model.adapter.SecurityUser;
import com.banco.co.user.repository.IUserCredential;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final IUserCredential userCredentialRepository;

    public CustomUserDetailsService(IUserCredential userCredentialRepository) {
        this.userCredentialRepository = userCredentialRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserCredential userCredential = userCredentialRepository
                .findByEmailWithRolesAndPermissions(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new SecurityUser(userCredential);
    }
}
