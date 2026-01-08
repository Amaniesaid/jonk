package com.imt.demo.steps;

import com.imt.demo.model.PipelineContext;
import com.imt.demo.model.StepResult;
import com.imt.demo.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Étape 5: Build de l'image Docker
 * Cette étape vérifie l'existence d'un Dockerfile et le génère automatiquement si nécessaire
 */
@Slf4j
@Component
public class DockerBuildStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Docker Build";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        StepResult result = StepResult.builder()
                .stepName(getName())
                .status(StepStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();

        try {
            // Vérifier et générer le Dockerfile si nécessaire
            File dockerfileFile = new File(context.getWorkspaceDir(), "Dockerfile");
            if (!dockerfileFile.exists() || dockerfileFile.length() == 0) {
                log.info("Dockerfile absent ou vide, génération automatique en cours...");
                result.addLog("⚠ Dockerfile absent ou vide, génération automatique en cours...");
                
                boolean generated = generateDockerfile(context.getWorkspaceDir(), result);
                if (!generated) {
                    result.setStatus(StepStatus.FAILED);
                    result.setErrorMessage("Échec de la génération du Dockerfile");
                    result.setEndTime(LocalDateTime.now());
                    result.calculateDuration();
                    return result;
                }
                result.addLog("✓ Dockerfile généré avec succès");
            } else {
                result.addLog("✓ Dockerfile existant détecté");
            }

            // Générer le tag de l'image
            String imageTag = context.getDockerImageTag() != null ?
                    context.getDockerImageTag() :
                    "latest-" + System.currentTimeMillis();

            String imageName = context.getDockerImageName();
            String fullImageName = imageName + ":" + imageTag;

            // Sauvegarder le tag pour les étapes suivantes
            context.setDockerImageTag(imageTag);

            result.addLog(" Construction de l'image Docker: " + fullImageName);

            // Commande Docker build
            String[] command = {
                "docker", "build",
                "-t", fullImageName,
                "."
            };

            StepResult buildResult = executeCommand(command, context.getWorkspaceDir());
            
            // Fusionner les résultats
            result.getLogs().addAll(buildResult.getLogs());
            result.setStatus(buildResult.getStatus());
            result.setErrorMessage(buildResult.getErrorMessage());
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();

            if (result.getStatus() == StepStatus.SUCCESS) {
                result.addLog("✓ Image Docker créée: " + fullImageName);
            }

        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage("Exception: " + e.getMessage());
            result.addLog("✗ Exception: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            log.error("Erreur lors du build Docker", e);
        }

        return result;
    }

    /**
     * Génère un Dockerfile optimisé pour un projet Maven Spring Boot
     * 
     * @param workspaceDir Le répertoire de travail du projet
     * @param result Le résultat de l'étape pour ajouter des logs
     * @return true si la génération a réussi, false sinon
     */
    private boolean generateDockerfile(String workspaceDir, StepResult result) {
        try {
            // Détecter la version Java du projet
            String javaVersion = detectJavaVersion(workspaceDir);
            result.addLog("  Version Java détectée: " + javaVersion);
            
            // Générer le contenu du Dockerfile
            String dockerfileContent = generateDockerfileContent(javaVersion);
            
            // Écrire le Dockerfile
            Path dockerfilePath = Paths.get(workspaceDir, "Dockerfile");
            Files.writeString(dockerfilePath, dockerfileContent);
            
            log.info("Dockerfile généré avec succès dans: {}", dockerfilePath);
            return true;
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération du Dockerfile", e);
            result.addLog("✗ Erreur lors de la génération du Dockerfile: " + e.getMessage());
            return false;
        }
    }

    /**
     * Détecte la version Java configurée dans le pom.xml
     * 
     * @param workspaceDir Le répertoire de travail du projet
     * @return La version Java (par défaut "17" si non trouvée)
     */
    private String detectJavaVersion(String workspaceDir) {
        try {
            File pomFile = new File(workspaceDir, "pom.xml");
            if (!pomFile.exists()) {
                log.warn("pom.xml non trouvé, utilisation de Java 17 par défaut");
                return "17";
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(pomFile);
            document.getDocumentElement().normalize();

            // Rechercher la version Java dans les propriétés
            NodeList properties = document.getElementsByTagName("properties");
            if (properties.getLength() > 0) {
                Element propsElement = (Element) properties.item(0);
                
                // Chercher java.version
                NodeList javaVersionNodes = propsElement.getElementsByTagName("java.version");
                if (javaVersionNodes.getLength() > 0) {
                    return javaVersionNodes.item(0).getTextContent().trim();
                }
                
                // Chercher maven.compiler.source
                NodeList compilerSourceNodes = propsElement.getElementsByTagName("maven.compiler.source");
                if (compilerSourceNodes.getLength() > 0) {
                    return compilerSourceNodes.item(0).getTextContent().trim();
                }
                
                // Chercher maven.compiler.target
                NodeList compilerTargetNodes = propsElement.getElementsByTagName("maven.compiler.target");
                if (compilerTargetNodes.getLength() > 0) {
                    return compilerTargetNodes.item(0).getTextContent().trim();
                }
            }

            log.warn("Version Java non trouvée dans pom.xml, utilisation de Java 17 par défaut");
            return "17";

        } catch (Exception e) {
            log.error("Erreur lors de la lecture du pom.xml", e);
            return "17";
        }
    }

    /**
     * Génère le contenu du Dockerfile optimisé pour Maven et Spring Boot
     * 
     * @param javaVersion La version Java du projet
     * @return Le contenu du Dockerfile
     */
    private String generateDockerfileContent(String javaVersion) {
        // Mapper les versions Java aux images appropriées
        String mavenImage = getMavenImage(javaVersion);
        String runtimeImage = getRuntimeImage(javaVersion);

        return String.format("""
            # Étape de build avec Maven
            FROM %s as build
            
            # Créer le répertoire de l'application et définir le répertoire de travail
            RUN mkdir -p /api
            WORKDIR /api
            
            # Copier le fichier pom.xml pour récupérer les dépendances
            COPY pom.xml /api
            
            # Précharger les dépendances Maven pour un build plus rapide
            RUN mvn dependency:go-offline -B
            
            # Copier le code source
            COPY src /api/src
            
            # Compiler le projet et créer le package
            RUN mvn -f /api/pom.xml clean package -DskipTests
            
            # Étape finale avec l'image de runtime
            FROM %s
            
            # Copier le fichier .jar généré à partir de l'étape précédente
            COPY --from=build /api/target/*.jar app.jar
            
            # Exposer le port utilisé par Spring Boot
            EXPOSE 8080/tcp
            
            # Commande d'entrée pour démarrer l'application
            ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "/app.jar"]
            """, mavenImage, runtimeImage);
    }

    /**
     * Obtient l'image Maven appropriée selon la version Java
     * 
     * @param javaVersion La version Java
     * @return Le nom de l'image Maven Docker
     */
    private String getMavenImage(String javaVersion) {
        return switch (javaVersion) {
            case "21" -> "maven:3.9.6-amazoncorretto-21";
            case "17" -> "maven:3.9.6-amazoncorretto-17";
            case "11" -> "maven:3.9.6-amazoncorretto-11";
            case "8" -> "maven:3.9.6-amazoncorretto-8";
            default -> {
                log.warn("Version Java non standard: {}, utilisation de Java 17", javaVersion);
                yield "maven:3.9.6-amazoncorretto-17";
            }
        };
    }

    /**
     * Obtient l'image de runtime appropriée selon la version Java
     * 
     * @param javaVersion La version Java
     * @return Le nom de l'image de runtime Docker
     */
    private String getRuntimeImage(String javaVersion) {
        return switch (javaVersion) {
            case "21" -> "amazoncorretto:21.0.5-alpine3.20";
            case "17" -> "amazoncorretto:17.0.13-alpine3.19";
            case "11" -> "amazoncorretto:11.0.25-alpine3.19";
            case "8" -> "amazoncorretto:8u432-alpine3.19";
            default -> {
                log.warn("Version Java non standard: {}, utilisation de Java 17", javaVersion);
                yield "amazoncorretto:17.0.13-alpine3.19";
            }
        };
    }
}

