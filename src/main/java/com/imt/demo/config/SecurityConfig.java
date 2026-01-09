package com.imt.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration de la sécurité de l'application.
 * Supporte OAuth2 avec Keycloak et peut fonctionner en mode "désactivé" pour les tests.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Configuration du filtre de sécurité
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // Désactiver CSRF pour API REST
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics
                        .requestMatchers("/api/pipeline/health").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/", "/error", "/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs.yaml","/v3/api-docs.yml", "/v3/api-docs/**","/api-docs/**").permitAll()

                        // Endpoints protégés - nécessite authentification
                        .requestMatchers("/api/pipeline/**").authenticated()

                        // Tous les autres endpoints nécessitent authentification
                        .anyRequest().authenticated()
                )
                // Configuration OAuth2 Resource Server (JWT)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    /**
     * Convertisseur JWT pour extraire les rôles depuis les claims Keycloak
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extraire les rôles depuis les claims standard
            JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
            Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);

            // Extraire les rôles depuis Keycloak (realm_access.roles)
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Collection<GrantedAuthority> keycloakRealmRoles = List.of();

            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) realmAccess.get("roles");
                keycloakRealmRoles = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList());
            }

            // Extraire les rôles depuis resource_access.jonk-back.roles
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            Collection<GrantedAuthority> resourceRoles = List.of();

            if (resourceAccess != null && resourceAccess.containsKey("jonk-back")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("jonk-back");
                if (clientAccess != null && clientAccess.containsKey("roles")) {
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) clientAccess.get("roles");
                    resourceRoles = roles.stream()
                            .map(SimpleGrantedAuthority::new) // Les rôles sont déjà préfixés avec ROLE_
                            .collect(Collectors.toList());
                }
            }

            // Combiner toutes les autorités
            return Stream.of(authorities.stream(), keycloakRealmRoles.stream(), resourceRoles.stream())
                    .flatMap(s -> s)
                    .collect(Collectors.toSet());
        });

        return converter;
    }

    /**
     * Configuration CORS pour permettre les requêtes depuis un frontend
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:4200", "http://jonk.local.fr:82"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
