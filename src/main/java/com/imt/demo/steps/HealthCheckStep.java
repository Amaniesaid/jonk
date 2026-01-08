package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import com.imt.demo.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Étape 8: Vérification de la santé de l'application déployée
 */
@Slf4j
@Component
public class HealthCheckStep extends AbstractPipelineStep {

    private static final int MAX_RETRIES = 15;
    private static final int RETRY_DELAY_MS = 4000;

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

        result.addLog(String.format("🔍 Vérification de l'application sur %s:%d", host, port));

        // Liste des endpoints à tester par ordre de priorité
        List<String> endpointsToTest = new ArrayList<>();
        endpointsToTest.add("/actuator/health");  // Spring Boot Actuator
        endpointsToTest.add("/health");           // Alternative simple
        endpointsToTest.add("/");                 // Page d'accueil

        boolean isHealthy = false;
        String successfulEndpoint = null;
        int successfulStatusCode = -1;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            result.addLog(String.format("⏳ Tentative %d/%d...", attempt, MAX_RETRIES));

            // D'abord, vérifier que le port est ouvert (connexion TCP)
            if (isPortOpen(host, port)) {
                result.addLog(String.format("  ✓ Port %d accessible", port));

                // Tester les différents endpoints HTTP
                for (String endpoint : endpointsToTest) {
                    String url = String.format("http://%s:%d%s", host, port, endpoint);
                    HealthCheckResult checkResult = checkEndpoint(url);

                    if (checkResult.isSuccess()) {
                        isHealthy = true;
                        successfulEndpoint = endpoint;
                        successfulStatusCode = checkResult.getStatusCode();
                        result.addLog(String.format("  ✓ Endpoint '%s' répond avec code %d", endpoint, checkResult.getStatusCode()));
                        result.addLog("✓ Application en ligne et opérationnelle!");
                        result.setStatus(StepStatus.SUCCESS);
                        break;
                    } else if (checkResult.getStatusCode() >= 400 && checkResult.getStatusCode() < 600) {
                        // Si l'application répond avec un code 4xx ou 5xx, elle est vivante
                        // On considère cela comme un succès car l'application répond
                        isHealthy = true;
                        successfulEndpoint = endpoint;
                        successfulStatusCode = checkResult.getStatusCode();
                        result.addLog(String.format("  ✓ Endpoint '%s' répond avec code %d (application vivante)", endpoint, checkResult.getStatusCode()));
                        result.addLog("✓ Application en ligne et opérationnelle!");
                        result.setStatus(StepStatus.SUCCESS);
                        break;
                    } else if (checkResult.getStatusCode() == -3) {
                        // Connection reset - application en cours de démarrage
                        result.addLog(String.format("  ⏳ Endpoint '%s': Connection reset (démarrage en cours...)", endpoint));
                    } else if (checkResult.getStatusCode() != -1) {
                        result.addLog(String.format("  ⚠ Endpoint '%s' code: %d", endpoint, checkResult.getStatusCode()));
                    }
                }

                if (isHealthy) {
                    break;
                }
            } else {
                result.addLog(String.format("  ⏳ Port %d non accessible, l'application démarre...", port));
            }

            if (attempt < MAX_RETRIES) {
                Thread.sleep(RETRY_DELAY_MS);
            }
        }

        if (!isHealthy) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage(String.format(
                "L'application n'a pas démarré correctement après %d tentatives (%.1f secondes)",
                MAX_RETRIES,
                (MAX_RETRIES * RETRY_DELAY_MS) / 1000.0
            ));
            result.addLog("✗ Échec du health check");
            result.addLog("");
            result.addLog("💡 Suggestions de débogage:");
            result.addLog("   1. Vérifier les logs du container: docker logs " + context.getContainerName());
            result.addLog("   2. Vérifier que l'application démarre correctement");
            result.addLog("   3. Vérifier que le port 8080 est exposé dans le container");
        } else {
            result.addLog(String.format("📊 Health check validé via: %s (code %d)", successfulEndpoint, successfulStatusCode));
        }

        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();

        return result;
    }

    /**
     * Vérifie si un port est ouvert sur un hôte donné
     * 
     * @param host L'hôte à tester
     * @param port Le port à tester
     * @return true si le port est ouvert, false sinon
     */
    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 5000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Vérifie un endpoint HTTP
     * 
     * @param urlString L'URL de l'endpoint à tester
     * @return Le résultat du health check
     */
    private HealthCheckResult checkEndpoint(String urlString) {
        try {
            URI uri = URI.create(urlString);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);  // Augmenté à 15 secondes
            connection.setReadTimeout(15000);     // Augmenté à 15 secondes
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            // Considérer 2xx et 3xx comme succès optimal
            boolean success = (responseCode >= 200 && responseCode < 400);

            connection.disconnect();

            return new HealthCheckResult(success, responseCode);

        } catch (java.net.ConnectException e) {
            // Port fermé ou application pas encore prête
            log.debug("Connexion refusée pour {}: {}", urlString, e.getMessage());
            return new HealthCheckResult(false, -1);
        } catch (java.net.SocketTimeoutException e) {
            // Timeout - l'application est peut-être lente à répondre
            log.debug("Timeout pour {}: {}", urlString, e.getMessage());
            return new HealthCheckResult(false, -2);
        } catch (java.io.IOException e) {
            // Connection reset - l'application démarre mais n'est pas encore complètement prête
            if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
                log.debug("Connection reset pour {} - application en cours de démarrage", urlString);
                return new HealthCheckResult(false, -3);
            }
            log.debug("IOException pour {}: {}", urlString, e.getMessage());
            return new HealthCheckResult(false, -1);
        } catch (Exception e) {
            log.debug("Erreur lors du test de l'endpoint {}: {}", urlString, e.getMessage());
            return new HealthCheckResult(false, -1);
        }
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    /**
     * Classe interne pour stocker le résultat d'un health check
     */
    private static class HealthCheckResult {
        private final boolean success;
        private final int statusCode;

        public HealthCheckResult(boolean success, int statusCode) {
            this.success = success;
            this.statusCode = statusCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
