package com.banco.co.user.adapter.out.jpa;

import com.banco.co.user.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper: UserEntity <-> User (domain).
 * Component model "spring" so it is injected as a Spring bean into UserJpaAdapter.
 */
@Mapper(componentModel = "spring")
interface UserEntityMapper {

    @Mapping(target = "roleId", ignore = true)
    User toDomain(UserEntity entity);

    @Mapping(target = "id", ignore = true)
    UserEntity toEntity(User domain);
}
