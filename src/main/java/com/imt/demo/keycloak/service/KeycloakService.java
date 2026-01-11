package com.imt.demo.keycloak.service;

import com.imt.demo.user.dto.UserDto;
import com.imt.demo.keycloak.mapper.KeycloakUserMapper;
import com.imt.demo.keycloak.properties.KeycloakProperties;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakService {
    private final Keycloak keycloak;
    private final KeycloakProperties properties;
    private final KeycloakUserMapper keycloakUserMapper = new KeycloakUserMapper();

    private String clientUUID;

    private String findClientUUID() {
        if (clientUUID == null) {
            RealmResource realm = getRealm();

            ClientRepresentation first = realm.clients().findByClientId(properties.getClientId()).getFirst();

            if (first == null) {
                throw new InternalServerErrorException("Could not find client " + properties.getClientId());
            }

            clientUUID = first.getId();
        }

        return clientUUID;
    }

    public List<UserDto> getUsers() {
        RealmResource realm = getRealm();
        UsersResource users = realm.users();

        return users
                .list()
                .stream()
                .map(userRepresentation -> toUserDto(userRepresentation, users.get(userRepresentation.getId())))
                .toList();
    }

    private RealmResource getRealm() {
        return keycloak.realm(properties.getRealm());
    }

    public void setAdminStatus(String id, boolean adminStatus, String undertakingAdmin) {
        log.info("User '{}' is setting user '{}' admin status to {}", undertakingAdmin, id, adminStatus);

        UserResource userResource = getRealm().users().get(id);

        if (userResource == null) {
            throw new BadRequestException("Could not find user " + id);
        }

        RoleScopeResource roleScopeResource = userResource.roles().clientLevel(findClientUUID());

        if (adminStatus) {
            Optional<RoleRepresentation> foundAdminRole = findAdminRole(roleScopeResource.listAvailable());
            foundAdminRole.ifPresent(adminRole -> roleScopeResource.add(List.of(adminRole)));
        } else {
            Optional<RoleRepresentation> foundAdminRole = findAdminRole(roleScopeResource.listEffective());
            foundAdminRole.ifPresent(adminRole -> roleScopeResource.remove(List.of(adminRole)));
        }
    }

    private Optional<RoleRepresentation> findAdminRole(List<RoleRepresentation> roles) {
        return roles
                .stream()
                .filter(role -> role.getName().equals(KeycloakUserMapper.ADMIN))
                .findFirst();
    }

    private UserDto toUserDto(UserRepresentation userRepresentation, UserResource userResource) {
        return keycloakUserMapper.toUserDto(userRepresentation, userResource, findClientUUID());
    }

    public UserDto getUser(String id) {
        RealmResource realm = getRealm();
        UsersResource users = realm.users();
        UserResource userResource = users.get(id);

        if (userResource == null) {
            throw new NotFoundException("No such user with id " + id);
        }

        return toUserDto(userResource.toRepresentation(), userResource);
    }
}
