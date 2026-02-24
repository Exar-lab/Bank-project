package com.banco.co.role.service;

import com.banco.co.role.enums.SystemRole;
import com.banco.co.role.exception.RoleNotFoundException;
import com.banco.co.role.model.Role;
import com.banco.co.role.repository.IRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService implements IRoleService {
    private final IRoleRepository roleRepository;
    @Override
    public Role findRoleByName(SystemRole name) {
        return roleRepository.findByName(name).orElseThrow(()-> new RoleNotFoundException(name.toString()));
    }
}
