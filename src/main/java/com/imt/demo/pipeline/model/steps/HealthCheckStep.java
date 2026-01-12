package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class HealthCheckStep extends AbstractPipelineStep {

    private static final int MAX_RETRIES = 15;
    private static final int RETRY_DELAY_MS = 4000;
    private static final String DEFAULT_CHECK_PORT = "8089";

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
        int port = context.getApplicationHostPort();

        result.addLog(String.format("Verification de l'application sur %s:%d", host, port));

        List<String> endpointsToTest = new ArrayList<>();
        endpointsToTest.add("/actuator/health");
        endpointsToTest.add("/health");
        endpointsToTest.add("/");

        boolean isHealthy = false;
        String successfulEndpoint = null;
        int successfulStatusCode = -1;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            result.addLog(String.format("Tentative %d/%d...", attempt, MAX_RETRIES));

            if (isPortOpen(host, port)) {
                result.addLog(String.format("Port %d accessible", port));

                for (String endpoint : endpointsToTest) {
                    String url = String.format("http://%s:%d%s", host, port, endpoint);
                    HealthCheckResult checkResult = checkEndpoint(url);

                    if (checkResult.isSuccess()) {
                        isHealthy = true;
                        successfulEndpoint = endpoint;
                        successfulStatusCode = checkResult.getStatusCode();
                        result.addLog(String.format("Endpoint '%s' repond avec code %d", endpoint, checkResult.getStatusCode()));
                        result.addLog("Application en ligne et operationnelle!");
                        result.setStatus(StepStatus.SUCCESS);
                        break;
                    } else if (checkResult.getStatusCode() >= 400 && checkResult.getStatusCode() < 600) {
                        isHealthy = true;
                        successfulEndpoint = endpoint;
                        successfulStatusCode = checkResult.getStatusCode();
                        result.addLog(String.format("Endpoint '%s' repond avec code %d (application vivante)", endpoint, checkResult.getStatusCode()));
                        result.addLog("Application en ligne et operationnelle!");
                        result.setStatus(StepStatus.SUCCESS);
                        break;
                    } else if (checkResult.getStatusCode() == -3) {
                        result.addLog(String.format("Endpoint '%s': Connection reset (demarrage en cours...)", endpoint));
                    } else if (checkResult.getStatusCode() != -1) {
                        result.addLog(String.format("Endpoint '%s' code: %d", endpoint, checkResult.getStatusCode()));
                    }
                }

                if (isHealthy) {
                    break;
                }
            } else {
                result.addLog(String.format("Port %d non accessible, l'application demarre...", port));
            }

            if (attempt < MAX_RETRIES) {
                Thread.sleep(RETRY_DELAY_MS);
            }
        }

        if (!isHealthy) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage(String.format(
                "L'application n'a pas demarre correctement apres %d tentatives (%.1f secondes)",
                MAX_RETRIES,
                (MAX_RETRIES * RETRY_DELAY_MS) / 1000.0
            ));
            result.addLog("Echec du health check");
            result.addLog("");
            result.addLog("Suggestions de debogage:");
            result.addLog("  1. Verifier les logs du container: docker logs " + context.getContainerName());
            result.addLog("  2. Verifier que l'application demarre correctement");
            result.addLog("  3. Verifier que le port 8080 est expose dans le container");
        } else {
            result.addLog(String.format("Health check valide via: %s (code %d)", successfulEndpoint, successfulStatusCode));
        }

        result.setEndTime(LocalDateTime.now());
        result.calculateDuration();

        return result;
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private HealthCheckResult checkEndpoint(String urlString) {
        try {
            URI uri = URI.create(urlString);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            boolean success = (responseCode >= 200 && responseCode < 400);

            connection.disconnect();

            return new HealthCheckResult(success, responseCode);

        } catch (java.net.ConnectException e) {
            log.debug("Connexion refusee pour {}: {}", urlString, e.getMessage());
            return new HealthCheckResult(false, -1);
        } catch (java.net.SocketTimeoutException e) {
            log.debug("Timeout pour {}: {}", urlString, e.getMessage());
            return new HealthCheckResult(false, -2);
        } catch (java.io.IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
                log.debug("Connection reset pour {} - application en cours de demarrage", urlString);
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
