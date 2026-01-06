package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import com.imt.demo.model.StepStatus;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Classe abstraite fournissant des méthodes utilitaires pour l'exécution de commandes système
 */
@Slf4j
public abstract class AbstractPipelineStep implements PipelineStep {

    /**
     * Exécute une commande système et capture les logs
     */
    protected StepResult executeCommand(String[] command, String workingDirectory,
                                        Map<String, String> environmentVariables) {
        StepResult result = StepResult.builder()
                .stepName(getName())
                .status(StepStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();

        try {
            log.info("Exécution de la commande: {}", String.join(" ", command));
            result.addLog("▶ Commande: " + String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            // Définir le répertoire de travail
            if (workingDirectory != null) {
                processBuilder.directory(new java.io.File(workingDirectory));
            }

            // Ajouter les variables d'environnement
            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                processBuilder.environment().putAll(environmentVariables);
            }

            // Rediriger stderr vers stdout
            processBuilder.redirectErrorStream(true);

            // Démarrer le processus
            Process process = processBuilder.start();

            // Lire la sortie en temps réel
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.addLog(line);
                    log.debug("[{}] {}", getName(), line);
                }
            }

            // Attendre la fin du processus
            int exitCode = process.waitFor();

            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();

            if (exitCode == 0) {
                result.setStatus(StepStatus.SUCCESS);
                result.addLog("✓ Étape terminée avec succès (code: " + exitCode + ")");
                log.info("Étape '{}' terminée avec succès", getName());
            } else {
                result.setStatus(StepStatus.FAILED);
                result.setErrorMessage("Commande échouée avec le code de sortie: " + exitCode);
                result.addLog("✗ Échec de l'étape (code: " + exitCode + ")");
                log.error("Étape '{}' échouée avec le code: {}", getName(), exitCode);
            }

        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage("Exception: " + e.getMessage());
            result.addLog("✗ Exception: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            log.error("Erreur lors de l'exécution de l'étape '{}'", getName(), e);
        }

        return result;
    }

    /**
     * Exécute une commande simple sans variables d'environnement
     */
    protected StepResult executeCommand(String[] command, String workingDirectory) {
        return executeCommand(command, workingDirectory, null);
    }

    /**
     * Exécute une liste de commandes séquentiellement
     */
    protected StepResult executeCommands(List<String[]> commands, String workingDirectory,
                                         Map<String, String> environmentVariables) {
        StepResult result = StepResult.builder()
                .stepName(getName())
                .status(StepStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();

        for (String[] command : commands) {
            StepResult commandResult = executeCommand(command, workingDirectory, environmentVariables);
            result.getLogs().addAll(commandResult.getLogs());

            if (commandResult.getStatus() == StepStatus.FAILED) {
                result.setStatus(StepStatus.FAILED);
                result.setErrorMessage(commandResult.getErrorMessage());
                result.setEndTime(LocalDateTime.now());
                result.calculateDuration();
                return result;
            }
        }

        result.setStatus(StepStatus.SUCCESS);
        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();
        return result;
    }

    @Override
    public void rollback(PipelineContext context) throws Exception {
        log.info("Rollback de l'étape: {} (pas d'action spécifique)", getName());
    }
}

