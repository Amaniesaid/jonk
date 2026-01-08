package com.imt.demo.sonarqube;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class SonarProjectManager {

    private final SonarApiClient apiClient;

    public SonarProjectManager(SonarApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public String computeProjectKey(File workspaceDir, String gitUrl) {
        // 1) Prefer Maven coordinates (groupId:artifactId) when pom.xml exists
        Optional<String> fromPom = computeProjectKeyFromPom(workspaceDir.toPath().resolve("pom.xml"));
        if (fromPom.isPresent()) {
            return sanitizeProjectKey(fromPom.get());
        }

        // 2) Fallback to git URL "org:repo"
        if (gitUrl != null && !gitUrl.isBlank()) {
            Optional<String> fromGit = computeProjectKeyFromGitUrl(gitUrl);
            if (fromGit.isPresent()) {
                return sanitizeProjectKey(fromGit.get());
            }
        }

        return sanitizeProjectKey("unknown:project");
    }

    public void ensureProjectExists(String projectKey, String projectName) {
        if (projectExists(projectKey)) {
            return;
        }

        log.info("Création du projet SonarQube '{}' (name='{}')", projectKey, projectName);
        apiClient.postForm("/api/projects/create", Map.of(
                "project", projectKey,
                "name", projectName
        ));
    }

    public boolean projectExists(String projectKey) {
        JsonNode json = apiClient.getJson("/api/projects/search", Map.of(
                "projects", projectKey
        ));

        JsonNode paging = json.get("paging");
        if (paging != null && paging.has("total")) {
            return paging.get("total").asInt(0) > 0;
        }

        JsonNode components = json.get("components");
        return components != null && components.isArray() && components.size() > 0;
    }

    private Optional<String> computeProjectKeyFromPom(Path pomPath) {
        try {
            if (!Files.exists(pomPath)) {
                return Optional.empty();
            }

            var dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setExpandEntityReferences(false);

            var db = dbf.newDocumentBuilder();
            var doc = db.parse(pomPath.toFile());
            doc.getDocumentElement().normalize();

            String artifactId = textContentOfFirst(doc.getDocumentElement(), "artifactId");
            String groupId = textContentOfFirst(doc.getDocumentElement(), "groupId");

            if (groupId == null || groupId.isBlank()) {
                // try parent groupId
                var parentNodes = doc.getDocumentElement().getElementsByTagName("parent");
                if (parentNodes.getLength() > 0) {
                    var parent = parentNodes.item(0);
                    if (parent instanceof org.w3c.dom.Element el) {
                        groupId = textContentOfFirst(el, "groupId");
                    }
                }
            }

            if (groupId == null || groupId.isBlank() || artifactId == null || artifactId.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(groupId.trim() + ":" + artifactId.trim());
        } catch (Exception e) {
            log.debug("Impossible de lire pom.xml pour projectKey: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String textContentOfFirst(org.w3c.dom.Element element, String tag) {
        var nodes = element.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private Optional<String> computeProjectKeyFromGitUrl(String gitUrl) {
        try {
            String normalized = gitUrl.trim();
            normalized = normalized.replaceAll("\\.git$", "");

            // support scp-like: git@github.com:org/repo
            if (normalized.contains(":") && normalized.contains("@") && !normalized.startsWith("http")) {
                int colon = normalized.indexOf(':');
                String path = normalized.substring(colon + 1);
                return orgRepoFromPath(path);
            }

            URI uri = URI.create(normalized);
            String path = uri.getPath();
            if (path == null) {
                return Optional.empty();
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return orgRepoFromPath(path);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> orgRepoFromPath(String path) {
        String cleaned = path;
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        String[] parts = cleaned.split("/");
        if (parts.length < 2) {
            return Optional.empty();
        }
        String org = parts[parts.length - 2];
        String repo = parts[parts.length - 1];
        if (org.isBlank() || repo.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(org + ":" + repo);
    }

    /**
     * SonarQube project key rules are fairly permissive but we sanitize to be safe.
     */
    private String sanitizeProjectKey(String raw) {
        String trimmed = raw.trim();
        // Keep common separators (':' '-' '_' '.') and alphanumerics; replace the rest.
        String sanitized = trimmed.replaceAll("[^a-zA-Z0-9:_\\-\\.]", "_");
        // Avoid extremely long keys (defensive)
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }
        // Sonar recommends avoiding upper-case in some contexts; keep but normalize to lower for stability
        return sanitized.toLowerCase(Locale.ROOT);
    }
}
