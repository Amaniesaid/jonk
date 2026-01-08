package com.imt.demo.controller;

import com.imt.demo.dto.SetAdminRequest;
import com.imt.demo.dto.UserDto;
import com.imt.demo.keycloak.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
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

    @PatchMapping("/{id}/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setAdminStatus(@PathVariable String id, @RequestBody SetAdminRequest setAdminRequest, Principal principal) {
        keycloakService.setAdminStatus(id, setAdminRequest.isAdmin(), principal.getName());
    }
}
