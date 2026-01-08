package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Étape 7: Déploiement de l'image Docker sur un serveur distant via SSH
 */
@Slf4j
@Component
public class DockerDeployStep extends AbstractPipelineStep {

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

        // Sauvegarder le nom du container pour le rollback
        context.setContainerName(containerName);

        // Si déploiement distant (SSH)
        if (context.getDeploymentHost() != null && context.getSshUser() != null) {
            return deployRemote(context, fullImageName, containerName);
        } else {
            // Déploiement local
            return deployLocal(context, fullImageName, containerName);
        }
    }

    /**
     * Déploiement local (sur la même machine)
     */
    private StepResult deployLocal(PipelineContext context, String imageName, String containerName) {
        List<String[]> commands = new ArrayList<>();

        // Vérifier et nettoyer le container existant si nécessaire
        if (containerExists(containerName)) {
            log.info("Container existant détecté ({}), nettoyage en cours...", containerName);
            
            // Arrêter le container existant
            commands.add(new String[]{"docker", "stop", containerName});
            
            // Supprimer le container
            commands.add(new String[]{"docker", "rm", containerName});
        } else {
            log.info("Aucun container existant nommé '{}', déploiement d'une nouvelle instance", containerName);
        }

        // Démarrer le nouveau container
        String[] runCommand = {
            "docker", "run",
            "-d",
            "--name", containerName,
            "-p", context.getDeploymentPort() + ":8080",
            imageName
        };
        commands.add(runCommand);

        StepResult result = executeCommands(commands, null, null);

        if (result.getStatus() == com.imt.demo.model.StepStatus.SUCCESS) {
            result.addLog("✓ Application déployée localement");
            result.addLog("  Container: " + containerName);
            result.addLog("  Port: " + context.getDeploymentPort());
            result.addLog("  Image: " + imageName);
        }

        return result;
    }

    /**
     * Déploiement distant via SSH
     */
    private StepResult deployRemote(PipelineContext context, String imageName, String containerName) {
        String sshTarget = context.getSshUser() + "@" + context.getDeploymentHost();

        List<String[]> commands = new ArrayList<>();

        // 1. Exporter l'image Docker vers un fichier tar
        String imageTarFile = "/tmp/" + context.getPipelineId() + ".tar";
        commands.add(new String[]{"docker", "save", "-o", imageTarFile, imageName});

        // 2. Copier l'image vers le serveur distant
        String[] scpCommand = {
            "scp",
            "-o", "StrictHostKeyChecking=no",
            imageTarFile,
            sshTarget + ":/tmp/"
        };

        if (context.getSshKeyPath() != null) {
            scpCommand = new String[]{
                "scp",
                "-i", context.getSshKeyPath(),
                "-o", "StrictHostKeyChecking=no",
                imageTarFile,
                sshTarget + ":/tmp/"
            };
        }
        commands.add(scpCommand);

        // 3. Charger l'image sur le serveur distant et déployer
        String remoteCommands = String.format(
            "docker load -i /tmp/%s.tar && " +
            "docker stop %s || true && " +
            "docker rm %s || true && " +
            "docker run -d --name %s -p %d:8080 %s && " +
            "rm /tmp/%s.tar",
            context.getPipelineId(),
            containerName,
            containerName,
            containerName,
            context.getDeploymentPort(),
            imageName,
            context.getPipelineId()
        );

        String[] sshCommand = {
            "ssh",
            "-o", "StrictHostKeyChecking=no",
            sshTarget,
            remoteCommands
        };

        if (context.getSshKeyPath() != null) {
            sshCommand = new String[]{
                "ssh",
                "-i", context.getSshKeyPath(),
                "-o", "StrictHostKeyChecking=no",
                sshTarget,
                remoteCommands
            };
        }
        commands.add(sshCommand);

        // 4. Nettoyer le fichier tar local
        commands.add(new String[]{"rm", imageTarFile});

        StepResult result = executeCommands(commands, null, null);

        if (result.getStatus() == com.imt.demo.model.StepStatus.SUCCESS) {
            result.addLog("✓ Application déployée sur " + context.getDeploymentHost());
            result.addLog("  Container: " + containerName);
            result.addLog("  Port: " + context.getDeploymentPort());
            result.addLog("  Image: " + imageName);
        }

        return result;
    }

    /**
     * Vérifie si un container Docker existe (en cours d'exécution ou arrêté)
     * 
     * @param containerName Le nom du container à vérifier
     * @return true si le container existe, false sinon
     */
    private boolean containerExists(String containerName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "ps", "-a", "--filter", "name=" + containerName, "--format", "{{.Names}}"
            );
            Process process = processBuilder.start();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exitCode = process.waitFor();
                
                // Vérifier si le nom exact correspond
                return exitCode == 0 && line != null && line.trim().equals(containerName);
            }
        } catch (Exception e) {
            log.debug("Erreur lors de la vérification du container: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void rollback(PipelineContext context) throws Exception {
        log.info("Rollback du déploiement...");

        if (context.getPreviousDockerImageTag() != null) {
            // Redéployer la version précédente
            String previousImage = context.getDockerImageName() + ":" + context.getPreviousDockerImageTag();
            String containerName = context.getContainerName();

            if (containerExists(containerName)) {
                // Arrêter le container actuel
                executeCommand(new String[]{"docker", "stop", containerName}, null);
                executeCommand(new String[]{"docker", "rm", containerName}, null);
            }

            // Redémarrer avec l'ancienne image
            String[] rollbackCommand = {
                "docker", "run",
                "-d",
                "--name", containerName,
                "-p", context.getDeploymentPort() + ":8080",
                previousImage
            };

            executeCommand(rollbackCommand, null);
            log.info("Rollback terminé: image {} restaurée", previousImage);
        }
    }
}

