package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DockerDeployStep extends AbstractPipelineStep {

    private static final String CONTAINER_PORT = "8089";
    private static final String APPLICATION_PORT = "8080";

    @Override
    public String getName() {
        return "Docker Deploy";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        String fullImageName = context.getDockerImageName() + ":" + context.getDockerImageTag();
        String containerName = context.getContainerName() != null ?
                context.getContainerName() :
                "jonk-app-" + context.getPipelineId();

        context.setContainerName(containerName);

        if (context.getDeploymentHost() != null && context.getSshUser() != null) {
            return deployRemote(context, fullImageName, containerName);
        } else {
            return deployLocal(context, fullImageName, containerName);
        }
    }

    private StepResult deployLocal(PipelineContext context, String imageName, String containerName) {
        List<String[]> commands = new ArrayList<>();

        if (containerExists(containerName)) {
            log.info("Container existant detecte ({}), nettoyage en cours...", containerName);
            commands.add(new String[]{"docker", "stop", containerName});
            commands.add(new String[]{"docker", "rm", containerName});
        } else {
            log.info("Aucun container existant nomme '{}', verification du port...", containerName);
        }

        String portInUse = findContainerUsingPort(context.getDeploymentPort());
        if (portInUse != null && !portInUse.equals(containerName)) {
            log.info("Port {} deja utilise par le container '{}', nettoyage...", context.getDeploymentPort(), portInUse);
            commands.add(new String[]{"docker", "stop", portInUse});
            commands.add(new String[]{"docker", "rm", portInUse});
        }

        String[] runCommand = {
            "docker", "run",
            "-d",
            "--name", containerName,
            "-p", CONTAINER_PORT + ":" + APPLICATION_PORT,
            imageName
        };
        commands.add(runCommand);

        StepResult result = executeCommands(commands, null, null);

        if (result.getStatus() == StepStatus.SUCCESS) {
            result.addLog("Application deployee localement");
            result.addLog("Container: " + containerName);
            result.addLog("Port: " + context.getDeploymentPort());
            result.addLog("Image: " + imageName);
        }

        return result;
    }

    private StepResult deployRemote(PipelineContext context, String imageName, String containerName) {
        String sshTarget = context.getSshUser() + "@" + context.getDeploymentHost();

        List<String[]> commands = new ArrayList<>();

        String imageTarFile = context.getWorkspaceDir() + context.getPipelineId() + ".tar";
        commands.add(new String[]{"docker", "save", "-o", imageTarFile, imageName});

        String[] scpCommand;
        if (context.getSshKeyPath() != null) {
            scpCommand = new String[]{
                "scp",
                "-P", context.getDeploymentPort(),
                "-i", context.getSshKeyPath(),
                "-o", "StrictHostKeyChecking=no",
                imageTarFile,
                sshTarget + ":/tmp/"
            };
        } else {
            scpCommand = new String[]{
                "scp",
                "-P", context.getDeploymentPort(),
                "-o", "StrictHostKeyChecking=no",
                imageTarFile,
                sshTarget + ":/tmp/"
            };
        }
        commands.add(scpCommand);

        String remoteCommands = String.format(
            "docker load -i /tmp/%s.tar && " +
            "docker stop %s || true && " +
            "docker rm %s || true && " +
            "docker run -d --name %s -p %s:%s %s && " +
            "rm /tmp/%s.tar",
            context.getPipelineId(),
            containerName,
            containerName,
            containerName,
            CONTAINER_PORT,
            APPLICATION_PORT,
            imageName,
            context.getPipelineId()
        );

        String[] sshCommand;
        if (context.getSshKeyPath() != null) {
            sshCommand = new String[]{
                "ssh",
                "-p", context.getDeploymentPort(),
                "-i", context.getSshKeyPath(),
                "-o", "StrictHostKeyChecking=no",
                sshTarget,
                remoteCommands
            };
        } else {
            sshCommand = new String[]{
                "ssh",
                "-p", context.getDeploymentPort(),
                "-o", "StrictHostKeyChecking=no",
                sshTarget,
                remoteCommands
            };
        }
        commands.add(sshCommand);

        StepResult result = executeCommands(commands, null, null);

        if (result.getStatus() == StepStatus.SUCCESS) {
            result.addLog("Application deployee sur " + context.getDeploymentHost());
            result.addLog("Container: " + containerName);
            result.addLog("Port: " + context.getDeploymentPort());
            result.addLog("Image: " + imageName);
        }

        return result;
    }

    private boolean containerExists(String containerName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "ps", "-a", "--filter", "name=" + containerName, "--format", "{{.Names}}"
            );
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exitCode = process.waitFor();
                
                return exitCode == 0 && line != null && line.trim().equals(containerName);
            }
        } catch (Exception e) {
            log.debug("Erreur lors de la verification du container: {}", e.getMessage());
            return false;
        }
    }

    private String findContainerUsingPort(String port) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "ps", "--filter", "publish=" + port, "--format", "{{.Names}}"
            );
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String containerName = reader.readLine();
                int exitCode = process.waitFor();
                
                if (exitCode == 0 && containerName != null && !containerName.trim().isEmpty()) {
                    return containerName.trim();
                }
                return null;
            }
        } catch (Exception e) {
            log.debug("Erreur lors de la recherche du container sur le port {}: {}", port, e.getMessage());
            return null;
        }
    }

    @Override
    public void rollback(PipelineContext context) throws Exception {
        log.info("Rollback du deploiement...");

        if (context.getPreviousDockerImageTag() != null) {
            String previousImage = context.getDockerImageName() + ":" + context.getPreviousDockerImageTag();
            String containerName = context.getContainerName();

            if (containerExists(containerName)) {
                executeCommand(new String[]{"docker", "stop", containerName}, null);
                executeCommand(new String[]{"docker", "rm", containerName}, null);
            }

            String[] rollbackCommand = {
                "docker", "run",
                "-d",
                "--name", containerName,
                "-p", CONTAINER_PORT + ":" + APPLICATION_PORT,
                previousImage
            };

            executeCommand(rollbackCommand, null);
            log.info("Rollback termine: image {} restauree", previousImage);
        }
    }
}
