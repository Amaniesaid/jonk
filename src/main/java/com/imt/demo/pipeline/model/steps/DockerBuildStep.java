package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
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

@Slf4j
@Component
public class DockerBuildStep extends AbstractPipelineStep {

    private static final String DEFAULT_JAVA_VERSION = "17";

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
            File dockerfileFile = new File(context.getWorkspaceDir(), "Dockerfile");
            if (!dockerfileFile.exists() || dockerfileFile.length() == 0) {
                log.info("Dockerfile absent ou vide, generation automatique en cours...");
                result.addLog("Dockerfile absent ou vide, generation automatique en cours...");
                
                boolean generated = generateDockerfile(context.getWorkspaceDir(), result);
                if (!generated) {
                    result.setStatus(StepStatus.FAILED);
                    result.setErrorMessage("Echec de la generation du Dockerfile");
                    result.setEndTime(LocalDateTime.now());
                    result.calculateDuration();
                    return result;
                }
                result.addLog("Dockerfile genere avec succes");
            } else {
                result.addLog("Dockerfile existant detecte");
            }

            String imageTag = context.getDockerImageTag() != null ?
                    context.getDockerImageTag() :
                    "latest-" + System.currentTimeMillis();

            String imageName = context.getDockerImageName();
            String fullImageName = imageName + ":" + imageTag;

            context.setDockerImageTag(imageTag);

            result.addLog("Construction de l'image Docker: " + fullImageName);

            String[] command = {
                "docker", "build",
                "-t", fullImageName,
                "."
            };

            StepResult buildResult = executeCommand(command, context.getWorkspaceDir());
            
            result.getLogs().addAll(buildResult.getLogs());
            result.setStatus(buildResult.getStatus());
            result.setErrorMessage(buildResult.getErrorMessage());
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();

            if (result.getStatus() == StepStatus.SUCCESS) {
                result.addLog("Image Docker creee: " + fullImageName);
            }

        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage("Exception: " + e.getMessage());
            result.addLog("Exception: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.calculateDuration();
            log.error("Erreur lors du build Docker", e);
        }

        return result;
    }

    private boolean generateDockerfile(String workspaceDir, StepResult result) {
        try {
            String javaVersion = detectJavaVersion(workspaceDir);
            result.addLog("Version Java detectee: " + javaVersion);
            
            String dockerfileContent = generateDockerfileContent(javaVersion);
            
            Path dockerfilePath = Paths.get(workspaceDir, "Dockerfile");
            Files.writeString(dockerfilePath, dockerfileContent);
            
            log.info("Dockerfile genere avec succes dans: {}", dockerfilePath);
            return true;
            
        } catch (Exception e) {
            log.error("Erreur lors de la generation du Dockerfile", e);
            result.addLog("Erreur lors de la generation du Dockerfile: " + e.getMessage());
            return false;
        }
    }

    private String detectJavaVersion(String workspaceDir) {
        try {
            File pomFile = new File(workspaceDir, "pom.xml");
            if (!pomFile.exists()) {
                log.warn("pom.xml non trouve, utilisation de Java {} par defaut", DEFAULT_JAVA_VERSION);
                return DEFAULT_JAVA_VERSION;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(pomFile);
            document.getDocumentElement().normalize();

            NodeList properties = document.getElementsByTagName("properties");
            if (properties.getLength() > 0) {
                Element propsElement = (Element) properties.item(0);
                
                NodeList javaVersionNodes = propsElement.getElementsByTagName("java.version");
                if (javaVersionNodes.getLength() > 0) {
                    return javaVersionNodes.item(0).getTextContent().trim();
                }
                
                NodeList compilerSourceNodes = propsElement.getElementsByTagName("maven.compiler.source");
                if (compilerSourceNodes.getLength() > 0) {
                    return compilerSourceNodes.item(0).getTextContent().trim();
                }
                
                NodeList compilerTargetNodes = propsElement.getElementsByTagName("maven.compiler.target");
                if (compilerTargetNodes.getLength() > 0) {
                    return compilerTargetNodes.item(0).getTextContent().trim();
                }
            }

            log.warn("Version Java non trouvee dans pom.xml, utilisation de Java {} par defaut", DEFAULT_JAVA_VERSION);
            return DEFAULT_JAVA_VERSION;

        } catch (Exception e) {
            log.error("Erreur lors de la lecture du pom.xml", e);
            return DEFAULT_JAVA_VERSION;
        }
    }

    private String generateDockerfileContent(String javaVersion) {
        String mavenImage = getMavenImage(javaVersion);
        String runtimeImage = getRuntimeImage(javaVersion);

        return String.format("""
            FROM %s as build
            
            RUN mkdir -p /api
            WORKDIR /api
            
            COPY pom.xml /api
            
            RUN mvn dependency:go-offline -B
            
            COPY src /api/src
            
            RUN mvn -f /api/pom.xml clean package -DskipTests
            
            FROM %s
            
            COPY --from=build /api/target/*.jar app.jar
            
            EXPOSE 8080/tcp
            
            ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "/app.jar"]
            """, mavenImage, runtimeImage);
    }

    private String getMavenImage(String javaVersion) {
        return switch (javaVersion) {
            case "21" -> "maven:3.9.6-amazoncorretto-21";
            case "17" -> "maven:3.9.6-amazoncorretto-17";
            case "11" -> "maven:3.9.6-amazoncorretto-11";
            case "8" -> "maven:3.9.6-amazoncorretto-8";
            default -> {
                log.warn("Version Java non standard: {}, utilisation de Java {}", javaVersion, DEFAULT_JAVA_VERSION);
                yield "maven:3.9.6-amazoncorretto-17";
            }
        };
    }

    private String getRuntimeImage(String javaVersion) {
        return switch (javaVersion) {
            case "21" -> "amazoncorretto:21.0.5-alpine3.20";
            case "17" -> "amazoncorretto:17.0.13-alpine3.19";
            case "11" -> "amazoncorretto:11.0.25-alpine3.19";
            case "8" -> "amazoncorretto:8u432-alpine3.19";
            default -> {
                log.warn("Version Java non standard: {}, utilisation de Java {}", javaVersion, DEFAULT_JAVA_VERSION);
                yield "amazoncorretto:17.0.13-alpine3.19";
            }
        };
    }
}
