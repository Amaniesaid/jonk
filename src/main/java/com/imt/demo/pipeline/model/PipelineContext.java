package com.imt.demo.pipeline.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineContext {

    private String gitUrl;
    private String branch;
    private String commitHash;

    private String workspaceDirectory;
    private File workspaceDir;

    private String buildTool;
    private String javaVersion;

    private String dockerImageName;
    private String dockerImageTag;
    private String dockerRegistry;
    private String previousDockerImageTag;

    private String sonarQubeUrl;
    private String sonarQubeToken;
    private String sonarProjectKey;

    private Boolean sonarEnabled;

    private String deploymentHost;
    private String deploymentUser;
    private int deploymentPort;
    private String sshKeyPath;
    private String deploymentPath;
    private Integer applicationHostPort;
    private Integer applicationPort;
    private String containerName;

    private String pipelineId;
    private String artifactPath;

    private String trivyCommand;
    private String trivySeverity;

    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();

    private String executionId;
    private String triggeredBy;

    public String getGitRepoUrl() {
        return gitUrl;
    }

    public String getGitBranch() {
        return branch;
    }

    public String getWorkspaceDir() {
        return workspaceDirectory;
    }

    public File getWorkspaceDirFile() {
        return workspaceDir;
    }

    public String getSonarUrl() {
        return sonarQubeUrl;
    }

    public String getSonarToken() {
        return sonarQubeToken;
    }

    public void addEnvironmentVariable(String key, String value) {
        if (this.environmentVariables == null) {
            this.environmentVariables = new HashMap<>();
        }
        this.environmentVariables.put(key, value);
    }

    public String getFullWorkspacePath() {
        return workspaceDirectory;
    }

    public String getFullDockerImageName() {
        if (dockerRegistry != null && !dockerRegistry.isEmpty()) {
            return dockerRegistry + "/" + dockerImageName + ":" + dockerImageTag;
        }
        return dockerImageName + ":" + dockerImageTag;
    }

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
