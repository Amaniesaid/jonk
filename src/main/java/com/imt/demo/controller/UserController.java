package com.imt.demo.controller;

import com.imt.demo.dto.UserDto;
import com.imt.demo.keycloak.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {
    private final KeycloakService keycloakService;

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEV')")
    public List<UserDto> getUsers() {
        return keycloakService.getUsers();
    }

}
