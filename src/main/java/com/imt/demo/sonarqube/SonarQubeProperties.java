package com.imt.demo.sonarqube;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Setter
@Getter
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

}
