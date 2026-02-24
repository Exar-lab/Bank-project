package com.banco.co.role.service;

import com.banco.co.role.enums.SystemRole;
import com.banco.co.role.model.Role;

public interface IRoleService {
    Role findRoleByName(SystemRole name);
}
