package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import com.imt.demo.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;

/**
 * Étape 8: Vérification de la santé de l'application déployée
 */
@Slf4j
@Component
public class HealthCheckStep extends AbstractPipelineStep {

    private static final int MAX_RETRIES = 10;
    private static final int RETRY_DELAY_MS = 5000;

    @Override
    public String getName() {
        return "Health Check";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        StepResult result = StepResult.builder()
                .stepName(getName())
                .status(StepStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();

        String host = context.getDeploymentHost() != null ? context.getDeploymentHost() : "localhost";
        String portStr = context.getDeploymentPort();
        Integer port = portStr != null ? Integer.parseInt(portStr) : 8080;

        String healthUrl = String.format("http://%s:%d/actuator/health", host, port);
        result.addLog(" Vérification de la santé de l'application: " + healthUrl);

        boolean isHealthy = false;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            result.addLog(String.format("Tentative %d/%d...", attempt, MAX_RETRIES));

            try {
                URL url = new URL(healthUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    isHealthy = true;
                    result.addLog("✓ Application en ligne et opérationnelle!");
                    result.setStatus(StepStatus.SUCCESS);
                    break;
                } else {
                    result.addLog(String.format("⚠ Code de réponse: %d", responseCode));
                }

                connection.disconnect();

            } catch (Exception e) {
                result.addLog(String.format(" Erreur: %s", e.getMessage()));
            }

            if (attempt < MAX_RETRIES) {
                Thread.sleep(RETRY_DELAY_MS);
            }
        }

        if (!isHealthy) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage("L'application n'a pas démarré correctement après " + MAX_RETRIES + " tentatives");
            result.addLog("✗ Échec du health check");
        }

        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();

        return result;
    }

    @Override
    public boolean isCritical() {
        return true;
    }
}
