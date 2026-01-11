package com.imt.demo.keycloak.mapper;

import com.imt.demo.user.dto.UserDto;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public class KeycloakUserMapper {
    public static final String ADMIN = "ROLE_ADMIN";

    public UserDto toUserDto(UserRepresentation userRepresentation, UserResource userModel, String clientId) {
        UserDto userDto = new UserDto();

        userDto.setId(userRepresentation.getId());
        userDto.setName(userRepresentation.getFirstName() + StringUtils.SPACE + userRepresentation.getLastName());
        userDto.setAdmin(userModel.roles()
                .clientLevel(clientId)
                .listEffective()
                .stream()
                .map(RoleRepresentation::getName)
                .anyMatch(s -> s.equals(ADMIN)));

        return userDto;
    }
}
