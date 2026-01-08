package com.imt.demo.keycloak;

import com.imt.demo.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
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
                throw new RuntimeException("Could not find client " + properties.getClientId());
            }

            clientUUID = first.getId();
        }

        return clientUUID;
    }

    public List<UserDto> getUsers() {
        RealmResource realm = getRealm();
        UsersResource users = realm.users();

        return  users
                .list()
                .stream()
                .map(userRepresentation -> keycloakUserMapper.toUserDto(userRepresentation, users.get(userRepresentation.getId()), findClientUUID()))
                .toList();
    }

    private RealmResource getRealm() {
        return keycloak.realm(properties.getRealm());
    }
}
