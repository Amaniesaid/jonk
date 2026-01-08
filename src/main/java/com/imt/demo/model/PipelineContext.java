package com.imt.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Contexte partagé entre toutes les étapes du pipeline.
 * Contient les informations nécessaires à l'exécution du pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineContext {

    // === Configuration Git ===
    private String gitUrl;
    private String branch;
    private String commitHash;

    // === Workspace ===
    private String workspaceDirectory;
    private File workspaceDir;

    // === Configuration Build ===
    private String buildTool; // maven, gradle
    private String javaVersion;

    // === Configuration Docker ===
    private String dockerImageName;
    private String dockerImageTag;
    private String dockerRegistry;
    private String previousDockerImageTag; // Pour rollback

    // === Configuration SonarQube ===
    private String sonarQubeUrl;
    private String sonarQubeToken;
    private String sonarProjectKey;

    // === Activation/désactivation des étapes ===
    private Boolean sonarEnabled;

    // === Configuration Déploiement ===
    private String deploymentHost;
    private String deploymentUser;
    private String deploymentPort;
    private String sshKeyPath;
    private String deploymentPath;
    private Integer applicationPort;
    private String sshUser;
    private String containerName;

    // === Métadonnées et tracking ===
    private String pipelineId;
    private String artifactPath;

    // === Configuration Trivy ===
    private String trivyCommand;
    private String trivySeverity; // LOW,MEDIUM,HIGH,CRITICAL

    // === Variables d'environnement personnalisées ===
    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();

    // === Métadonnées ===
    private String executionId;
    private String triggeredBy;

    // Aliases pour compatibilité
    public String getGitRepoUrl() {
        return gitUrl;
    }

    public String getGitBranch() {
        return branch;
    }

    public String getWorkspaceDir() {
        return workspaceDirectory;
    }

    /**
     * Accès explicite au workspace sous forme de File.
     * (Le getter getWorkspaceDir() est un alias historique retournant un String.)
     */
    public File getWorkspaceDirFile() {
        return workspaceDir;
    }

    public String getSonarUrl() {
        return sonarQubeUrl;
    }

    public String getSonarToken() {
        return sonarQubeToken;
    }

    /**
     * Ajoute une variable d'environnement au contexte
     */
    public void addEnvironmentVariable(String key, String value) {
        if (this.environmentVariables == null) {
            this.environmentVariables = new HashMap<>();
        }
        this.environmentVariables.put(key, value);
    }

    /**
     * Récupère le chemin complet du workspace
     */
    public String getFullWorkspacePath() {
        return workspaceDirectory;
    }

    /**
     * Construit le nom complet de l'image Docker
     */
    public String getFullDockerImageName() {
        if (dockerRegistry != null && !dockerRegistry.isEmpty()) {
            return dockerRegistry + "/" + dockerImageName + ":" + dockerImageTag;
        }
        return dockerImageName + ":" + dockerImageTag;
    }

    /**
     * Construit le nom de l'image Docker précédente (pour rollback)
     */
    public String getPreviousFullDockerImageName() {
        if (previousDockerImageTag == null) {
            return null;
        }
        if (dockerRegistry != null && !dockerRegistry.isEmpty()) {
            return dockerRegistry + "/" + dockerImageName + ":" + previousDockerImageTag;
        }
        return dockerImageName + ":" + previousDockerImageTag;
    }
}
