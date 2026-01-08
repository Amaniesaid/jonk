package com.imt.demo.sonarqube;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "jonk.sonarqube")
public class SonarQubeProperties {

    /**
     * DEV: URL fixe (ex: http://localhost:9000)
     */
    private String hostUrl = "http://localhost:9000";

    /**
     * DEV: token en dur dans application-dev.yml (facilement remplaçable par un secret manager)
     */
    private String token = "squ_9559693dc48eac5d244a0b087ffb08f72cdea3d5";

    /**
     * Commande du SonarScanner CLI (doit être disponible dans l'environnement)
     */
    private String scannerCommand = "sonar-scanner";

    /**
     * Polling qualité: intervalle entre requêtes.
     */
    private Duration pollInterval = Duration.ofSeconds(2);

    /**
     * Timeout global pour l'attente de la fin d'analyse + Quality Gate.
     */
    private Duration qualityGateTimeout = Duration.ofMinutes(5);

    public String getHostUrl() {
        return hostUrl;
    }

    public void setHostUrl(String hostUrl) {
        this.hostUrl = hostUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getScannerCommand() {
        return scannerCommand;
    }

    public void setScannerCommand(String scannerCommand) {
        this.scannerCommand = scannerCommand;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getQualityGateTimeout() {
        return qualityGateTimeout;
    }

    public void setQualityGateTimeout(Duration qualityGateTimeout) {
        this.qualityGateTimeout = qualityGateTimeout;
    }
}
