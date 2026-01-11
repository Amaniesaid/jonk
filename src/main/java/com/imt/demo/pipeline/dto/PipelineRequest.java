package com.imt.demo.pipeline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRequest {

    private String gitUrl;
    private String branch;

    private String buildTool;

    private String dockerImageName;
    private String dockerPort;
    private String dockerImageTag;
    private String dockerRegistry;

    private String sonarQubeUrl;
    private String sonarQubeToken;
    private String sonarProjectKey;

    private Boolean sonarEnabled;

    private String deploymentHost;
    private String deploymentUser;
    private String deploymentPort;
    private String sshKeyPath;

    private Map<String, String> environmentVariables;

    private String triggeredBy;
}
