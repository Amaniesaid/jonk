# 🏗️ Architecture Technique - JONK CI/CD Engine

## 📐 Vue d'ensemble

JONK est construit selon une **architecture en couches** avec séparation claire des responsabilités :

```
┌─────────────────────────────────────────────────────────┐
│                    API Layer (REST)                     │
│                 PipelineController                      │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Service Layer                          │
│               PipelineService                           │
│         (Logique métier + Async)                        │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Engine Layer                           │
│               PipelineEngine                            │
│      (Orchestration + Rollback + Workspace)             │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Steps Layer                            │
│  GitClone, MavenBuild, MavenTest, SonarQube,           │
│  DockerBuild, DockerScan, DockerDeploy, HealthCheck    │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Persistence Layer                          │
│       PipelineExecutionRepository (MongoDB)             │
└─────────────────────────────────────────────────────────┘
```

---

## 🔍 Détails des composants

### 1. API Layer - PipelineController

**Responsabilités :**
- Exposition des endpoints REST
- Validation des requêtes entrantes
- Conversion DTO ↔ Modèle
- Gestion des erreurs HTTP

**Endpoints principaux :**
| Endpoint | Méthode | Description |
|----------|---------|-------------|
| `/api/pipeline/run` | POST | Déclenche un nouveau pipeline |
| `/api/pipeline/{id}` | GET | Récupère les détails d'un pipeline |
| `/api/pipeline/{id}/logs` | GET | Récupère les logs d'un pipeline |
| `/api/pipeline/executions` | GET | Liste les exécutions récentes |
| `/api/pipeline/{id}/cancel` | POST | Annule un pipeline en cours |
| `/api/pipeline/health` | GET | Health check de l'API |

**Sécurité :**
- Annotations `@PreAuthorize` pour contrôle d'accès basé sur les rôles
- Support OAuth2 / JWT avec Keycloak

---

### 2. Service Layer - PipelineService

**Responsabilités :**
- Logique métier du pipeline
- Gestion de l'exécution asynchrone (`@Async`)
- Construction dynamique des étapes
- Persistance dans MongoDB
- Gestion du cycle de vie des pipelines

**Méthodes clés :**
```java
@Async("pipelineExecutor")
String runPipelineAsync(PipelineContext context)
    // Lance un pipeline en arrière-plan

PipelineExecution runPipelineSync(PipelineContext context)
    // Lance un pipeline de manière synchrone (tests)

List<PipelineStep> buildPipelineSteps(PipelineContext context)
    // Construit la liste des étapes à exécuter

List<String> getExecutionLogs(String executionId)
    // Récupère les logs formatés
```

**Injection des dépendances :**
- Toutes les étapes du pipeline sont injectées via Spring
- Le `PipelineEngine` est injecté
- Le `PipelineExecutionRepository` est injecté

---

### 3. Engine Layer - PipelineEngine

**Responsabilités :**
- Orchestration séquentielle des étapes
- Préparation du workspace temporaire
- Gestion des erreurs et arrêt du pipeline
- Déclenchement du rollback automatique
- Nettoyage des ressources

**Workflow d'exécution :**
```java
1. validateContext()      // Validation du contexte
2. prepareWorkspace()     // Création workspace temporaire
3. Pour chaque étape :
   a. step.execute()      // Exécution de l'étape
   b. Si échec :
      - Arrêt du pipeline
      - performRollback()  // Rollback des étapes critiques
4. cleanupWorkspace()     // Nettoyage du workspace
5. Retour PipelineExecution
```

**Gestion du workspace :**
- Workspace isolé dans `/tmp/jonk-pipelines/{uuid}/`
- Nettoyage automatique après exécution
- Gestion des erreurs de création/suppression

---

### 4. Steps Layer - Étapes modulaires

**Interface commune : PipelineStep**
```java
public interface PipelineStep {
    String getName();                              // Nom de l'étape
    StepResult execute(PipelineContext context);   // Exécution
    void rollback(PipelineContext context);        // Rollback
    boolean isCritical();                          // Critique ou non
}
```

**Classe de base : AbstractPipelineStep**
- Fournit des méthodes utilitaires
- `executeCommand()` : Exécute une commande via ProcessBuilder
- `executeCommands()` : Exécute plusieurs commandes séquentiellement
- Capture stdout/stderr en temps réel
- Gestion des codes de sortie

#### Étapes implémentées

##### 1️⃣ GitCloneStep
**Commande :** `git clone --branch {branch} --depth 1 {url} .`

**Actions :**
- Clone le repository dans le workspace
- Récupère le hash du commit (`git rev-parse HEAD`)
- Sauvegarde le commit hash dans le contexte

**Rollback :** Supprime le workspace

---

##### 2️⃣ MavenBuildStep
**Commande :** `mvn clean package -DskipTests -B`

**Actions :**
- Compile le projet Java
- Génère le JAR dans `target/`
- Sauvegarde le chemin de l'artifact dans le contexte

**Rollback :** Aucun

---

##### 3️⃣ MavenTestStep
**Commande :** `mvn test -B`

**Actions :**
- Exécute les tests unitaires
- Génère les rapports de tests

**Critique :** ✅ Oui (arrête le pipeline si échec)

---

##### 4️⃣ SonarQubeStep
**Commande :** `mvn sonar:sonar -Dsonar.host.url={url} -Dsonar.token={token}`

**Actions :**
- Analyse la qualité du code
- Envoie les résultats à SonarQube

**Critique :** ❌ Non (optionnel, ignoré si non configuré)

---

##### 5️⃣ DockerBuildStep
**Commande :** `docker build -t {image}:{tag} .`

**Actions :**
- Build l'image Docker
- Tag l'image avec le nom/tag spécifié

**Critique :** ✅ Oui

---

##### 6️⃣ DockerScanStep
**Commande :** `trivy image --severity MEDIUM,HIGH,CRITICAL {image}`

**Actions :**
- Scan de sécurité de l'image
- Détecte les vulnérabilités

**Critique :** ❌ Non (optionnel, ignoré si Trivy non installé)

---

##### 7️⃣ DockerDeployStep
**Commande locale :**
```bash
docker stop {container} || true
docker rm {container} || true
docker run -d --name {container} -p {port}:8080 {image}
```

**Commande distante (SSH) :**
```bash
docker save -o /tmp/{image}.tar {image}
scp /tmp/{image}.tar {user}@{host}:/tmp/
ssh {user}@{host} "docker load -i /tmp/{image}.tar && docker run ..."
```

**Rollback :** Redéploie l'ancienne version de l'image

---

##### 8️⃣ HealthCheckStep
**Commande :** HTTP GET `http://{host}:{port}/actuator/health`

**Actions :**
- Vérifie que l'application démarre correctement
- Retry automatique (10 tentatives, 5s d'intervalle)
- Attend une réponse HTTP 200

**Critique :** ✅ Oui (déclenche rollback si échec)

---

### 5. Model Layer

#### PipelineContext
Contexte partagé entre toutes les étapes :
```java
- gitUrl, branch, commitHash          // Configuration Git
- workspaceDirectory                   // Workspace temporaire
- dockerImageName, dockerImageTag      // Configuration Docker
- deploymentHost, deploymentPort       // Configuration déploiement
- sonarQubeUrl, sonarQubeToken         // Configuration SonarQube
- environmentVariables                 // Variables d'env personnalisées
```

#### PipelineExecution
Représentation d'une exécution de pipeline (MongoDB) :
```java
- id                                   // UUID unique
- gitRepoUrl, gitBranch, commitHash    // Infos Git
- status                               // PENDING, RUNNING, SUCCESS, FAILED, etc.
- startTime, endTime, durationMs       // Timing
- steps[]                              // Liste des StepResult
- errorMessage                         // Message d'erreur si échec
- triggeredBy                          // Utilisateur qui a lancé
```

#### StepResult
Résultat de l'exécution d'une étape :
```java
- stepName                             // Nom de l'étape
- status                               // PENDING, RUNNING, SUCCESS, FAILED
- startTime, endTime, durationMs       // Timing
- logs[]                               // Liste des logs
- errorMessage                         // Message d'erreur si échec
```

---

### 6. Persistence Layer

**PipelineExecutionRepository** (MongoDB)

Interface Spring Data MongoDB :
```java
List<PipelineExecution> findByStatus(PipelineStatus status)
List<PipelineExecution> findByGitRepoUrl(String url)
List<PipelineExecution> findByTriggeredBy(String user)
List<PipelineExecution> findTop10ByOrderByStartTimeDesc()
```

**Collections MongoDB :**
- `pipeline_executions` : Stocke toutes les exécutions

---

## 🔄 Flux de données

### Lancement d'un pipeline

```
1. Client → POST /api/pipeline/run
            ↓ (PipelineRequest)
2. PipelineController.runPipeline()
            ↓ (validation)
3. PipelineContext = buildContextFromRequest()
            ↓
4. PipelineService.runPipelineAsync()
            ↓ (@Async - nouveau thread)
5. PipelineEngine.executePipeline()
            ↓ (séquentiellement)
6. Pour chaque PipelineStep :
   - step.execute(context)
   - Sauvegarde StepResult
            ↓
7. PipelineExecution → MongoDB
            ↓
8. Client ← 202 Accepted (executionId)
```

### Consultation d'un pipeline

```
1. Client → GET /api/pipeline/{id}
            ↓
2. PipelineController.getPipeline(id)
            ↓
3. PipelineService.getExecution(id)
            ↓
4. MongoDB → PipelineExecution
            ↓
5. PipelineResponse.fromExecution()
            ↓
6. Client ← 200 OK (PipelineResponse)
```

---

## ⚙️ Configuration

### AsyncConfig
- **ThreadPoolTaskExecutor** dédié aux pipelines
- Core pool size: 2 threads
- Max pool size: 5 threads
- Queue capacity: 50 pipelines en attente

### SecurityConfig
- **OAuth2 Resource Server** avec JWT
- Extraction des rôles depuis Keycloak (`realm_access.roles`)
- CORS configuré pour frontend (localhost:3000, localhost:4200)
- Endpoints publics : `/health`, `/actuator/**`

### MongoDB
- Auto-indexation des collections
- URI par défaut : `mongodb://localhost:27017/jonk-cicd`

---

## 🛠️ Patterns de conception utilisés

### 1. **Strategy Pattern**
- Interface `PipelineStep` + implémentations concrètes
- Permet d'ajouter facilement de nouvelles étapes

### 2. **Builder Pattern**
- Tous les modèles utilisent `@Builder` (Lombok)
- Construction fluide des objets

### 3. **Template Method Pattern**
- `AbstractPipelineStep` fournit les méthodes communes
- Les classes concrètes implémentent `execute()`

### 4. **Repository Pattern**
- `PipelineExecutionRepository` abstrait l'accès aux données
- Spring Data MongoDB génère l'implémentation

### 5. **Dependency Injection**
- Spring gère toutes les dépendances
- Facilite les tests et la maintenance

---

## 🧪 Points d'extension

### Ajouter une nouvelle étape

1. Créer une classe qui `extends AbstractPipelineStep` :
```java
@Component
public class MyNewStep extends AbstractPipelineStep {
    @Override
    public String getName() {
        return "My New Step";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        String[] command = {"my-command", "arg1", "arg2"};
        return executeCommand(command, context.getWorkspaceDir());
    }

    @Override
    public boolean isCritical() {
        return true;
    }
}
```

2. Injecter dans `PipelineService` :
```java
@RequiredArgsConstructor
public class PipelineService {
    private final MyNewStep myNewStep;

    private List<PipelineStep> buildPipelineSteps(PipelineContext context) {
        steps.add(myNewStep); // Ajouter à la position souhaitée
    }
}
```

### Ajouter un nouveau endpoint

1. Ajouter la méthode dans `PipelineController` :
```java
@GetMapping("/my-endpoint")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> myEndpoint() {
    // Logique
}
```

### Ajouter un nouveau champ dans PipelineContext

1. Ajouter dans `PipelineContext.java` :
```java
private String myNewField;
```

2. Ajouter dans `PipelineRequest.java` :
```java
private String myNewField;
```

3. Mapper dans `PipelineController.buildContextFromRequest()` :
```java
.myNewField(request.getMyNewField())
```

---

## 📊 Performance et scalabilité

### Performance actuelle
- **Pipeline moyen** : 3-5 minutes (selon le projet)
- **Concurrent pipelines** : Max 5 simultanés
- **Queue** : 50 pipelines en attente
- **Workspace** : ~500MB par pipeline (nettoyé après)

### Optimisations possibles
1. **Cache Maven** : Réduire le temps de build
2. **Cache Docker** : Réutiliser les layers
3. **Parallélisation** : Exécuter des étapes indépendantes en parallèle
4. **Streaming logs** : WebSocket pour logs temps réel
5. **Kubernetes** : Déploiement scalable des pipelines

---

## 🔒 Sécurité

### Actuellement implémenté
- ✅ OAuth2 / JWT ready
- ✅ Contrôle d'accès basé sur les rôles
- ✅ Validation des entrées
- ✅ CORS configuré

### À améliorer (production)
- 🔐 Secrets management (Vault)
- 🔐 Encryption des logs sensibles
- 🔐 Rate limiting
- 🔐 Audit logging
- 🔐 SSH key rotation

---

**Cette architecture garantit :**
- ✅ Extensibilité
- ✅ Maintenabilité
- ✅ Testabilité
- ✅ Scalabilité
