package com.imt.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO pour la requête de déclenchement d'un pipeline
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRequest {

    // === Configuration Git (obligatoire) ===
    private String gitUrl;
    private String branch;

    // === Configuration Build ===
    private String buildTool; // maven, gradle (défaut: maven)

    // === Configuration Docker (obligatoire) ===
    private String dockerImageName;
    private String dockerImageTag; // optionnel (généré automatiquement si absent)
    private String dockerRegistry; // optionnel

    // === Configuration SonarQube (optionnel) ===
    private String sonarQubeUrl;
    private String sonarQubeToken;
    private String sonarProjectKey;

    // === Configuration Déploiement (optionnel) ===
    private String deploymentHost; // null = déploiement local
    private String deploymentUser;
    private String deploymentPort; // ex: "8080"
    private String sshKeyPath;

    // === Variables d'environnement personnalisées ===
    private Map<String, String> environmentVariables;

    // === Métadonnées ===
    private String triggeredBy; // Nom de l'utilisateur qui déclenche le pipeline
}
