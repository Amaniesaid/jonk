# 🎤 Présentation JONK - Moteur CI/CD Custom

## 📋 Plan de la soutenance (15 minutes)

---

### 1️⃣ INTRODUCTION (2 minutes)

#### Contexte du projet
- **Problématique** : "Comment fonctionne réellement un moteur CI/CD ?"
- **Contrainte** : Développement **from scratch**, sans Jenkins/GitLab CI/GitHub Actions
- **Objectif** : Comprendre et maîtriser l'orchestration de pipelines CI/CD

#### Présentation de JONK
- **J**ava **O**rchestration e**N**gine for **K**ontinuous integration
- Moteur CI/CD complet et fonctionnel
- 8 étapes de pipeline intégrées
- API REST pour déclenchement et suivi

---

### 2️⃣ ARCHITECTURE TECHNIQUE (3 minutes)

#### Vue d'ensemble
```
┌─────────────────┐
│   API REST      │ ← Déclenchement du pipeline
│ (Controller)    │
└────────┬────────┘
         │
┌────────▼────────┐
│ PipelineService │ ← Logique métier + Async
└────────┬────────┘
         │
┌────────▼────────┐
│ PipelineEngine  │ ← Orchestration + Rollback
└────────┬────────┘
         │
    ┌────▼────┐
    │  Steps  │ ← 8 étapes modulaires
    └─────────┘
```

#### Composants clés

**PipelineEngine**
- Orchestrateur central
- Gestion des erreurs
- Rollback automatique
- Workspace isolé

**PipelineStep (Interface)**
```java
- execute(context)      // Exécution
- rollback(context)     // Annulation
- isCritical()          // Détermine si échec = rollback
```

**PipelineContext**
- Contexte partagé entre étapes
- Configuration Git, Docker, Deploy
- Variables d'environnement

#### Technologies
- ✅ **Spring Boot 3.5** - Framework backend
- ✅ **Java 21** - Langage
- ✅ **MongoDB** - Persistance
- ✅ **ProcessBuilder** - Exécution commandes système
- ✅ **OAuth2/Keycloak** - Sécurité (prêt)

---

### 3️⃣ DÉMONSTRATION LIVE (5 minutes)

#### Scénario démo : Pipeline Spring PetClinic

**Étape 1 : Vérifier que l'API fonctionne**
```bash
curl http://localhost:8080/api/pipeline/health
```

**Étape 2 : Lancer un pipeline complet**
```bash
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Content-Type: application/json" \
  -d @examples/request-simple.json
```

Réponse :
```json
{
  "executionId": "a1b2c3d4...",
  "message": "Pipeline démarré avec succès",
  "status": "RUNNING"
}
```

**Étape 3 : Observer les logs console en temps réel**
```
🚀 Démarrage du pipeline: a1b2c3d4-...
📁 Repository: https://github.com/spring-projects/spring-petclinic.git
▶️  Exécution de l'étape: Git Clone
✅ Étape 'Git Clone' terminée avec succès en 2340ms
▶️  Exécution de l'étape: Maven Build
...
```

**Étape 4 : Consulter le statut du pipeline**
```bash
curl http://localhost:8080/api/pipeline/{executionId}
```

**Étape 5 : Récupérer les logs détaillés**
```bash
curl http://localhost:8080/api/pipeline/{executionId}/logs
```

**Étape 6 : Montrer MongoDB**
```bash
# Connexion MongoDB
mongo jonk-cicd

# Afficher les exécutions
db.pipeline_executions.find().pretty()
```

#### Points à souligner pendant la démo
- ✅ Exécution **réelle** des commandes (git, maven, docker)
- ✅ Logs **en temps réel** dans la console
- ✅ Persistance dans **MongoDB**
- ✅ API REST **responsive**

---

### 4️⃣ LES 8 ÉTAPES DU PIPELINE (2 minutes)

| # | Étape | Outil utilisé | Critique | Rollback |
|---|-------|---------------|----------|----------|
| 1 | **GitCloneStep** | `git clone` | ✅ | Supprime workspace |
| 2 | **MavenBuildStep** | `mvn clean package` | ✅ | - |
| 3 | **MavenTestStep** | `mvn test` | ✅ | - |
| 4 | **SonarQubeStep** | `mvn sonar:sonar` | ❌ | - |
| 5 | **DockerBuildStep** | `docker build` | ✅ | - |
| 6 | **DockerScanStep** | `trivy image` | ❌ | - |
| 7 | **DockerDeployStep** | `docker run` | ✅ | Redéploie ancienne version |
| 8 | **HealthCheckStep** | HTTP GET `/actuator/health` | ✅ | - |

#### Explication du rollback
Si **HealthCheckStep** échoue :
1. Arrêt du pipeline
2. Rollback des étapes critiques (ordre inverse)
3. Exemple : Arrêt du nouveau container, redéploiement de l'ancien
4. Nettoyage du workspace

---

### 5️⃣ CHOIX TECHNIQUES JUSTIFIÉS (2 minutes)

#### Pourquoi ProcessBuilder ?
✅ **Contrôle total** sur l'exécution  
✅ **Capture stdout/stderr** en temps réel  
✅ **Gestion des codes de sortie**  
✅ **Variables d'environnement** personnalisables  

```java
ProcessBuilder processBuilder = new ProcessBuilder(command);
processBuilder.directory(new File(workingDirectory));
processBuilder.environment().putAll(environmentVariables);
Process process = processBuilder.start();
```

#### Pourquoi MongoDB ?
✅ **Structure flexible** (logs de taille variable)  
✅ **Requêtes rapides** sur métadonnées  
✅ **Pas de schéma rigide** (évolution facile)  

#### Pourquoi architecture modulaire (Steps) ?
✅ **Ajout facile** de nouvelles étapes  
✅ **Réutilisabilité** du code  
✅ **Tests unitaires** simplifiés  
✅ **Maintenance** facilitée  

---

### 6️⃣ SÉCURITÉ (1 minute)

#### OAuth2 / Keycloak (prêt à l'emploi)

**Configuration dans application.properties** :
```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/jonk
```

**Rôles implémentés** :
- `ADMIN` : Tous les droits
- `DEV` : Lancer et consulter pipelines
- `VIEWER` : Consultation uniquement

**Requête authentifiée** :
```bash
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '...'
```

---

### 7️⃣ LIMITES ET ÉVOLUTIONS (1 minute)

#### Limitations actuelles
- ❌ Pas de parallélisation des étapes
- ❌ Rollback partiel uniquement
- ❌ Pas de cache de build
- ❌ Logs en mémoire (risque de saturation)

#### Évolutions futures
- 🔮 **WebSocket** pour logs temps réel
- 🔮 **Parallélisation** des étapes indépendantes
- 🔮 **Cache Maven/Docker** pour performance
- 🔮 **Support Kubernetes** pour déploiement
- 🔮 **Dashboard Web** (React/Vue.js)
- 🔮 **Webhooks GitLab/GitHub** pour déclenchement auto

---

### 8️⃣ CONCLUSION (1 minute)

#### Ce que nous avons appris
✅ **Comprendre** le fonctionnement interne des CI/CD  
✅ **Maîtriser** l'exécution de commandes système en Java  
✅ **Gérer** les erreurs et le rollback dans un système distribué  
✅ **Architecturer** une application modulaire et extensible  

#### Utilité pour le Cloud Sécurisé
- **Contrôle total** sur le pipeline (audit de sécurité)
- **Traçabilité complète** (logs MongoDB)
- **Sécurisation par OAuth2** (intégration Keycloak)
- **Déploiement automatisé** sur infrastructure cloud

#### Message final
> "JONK démontre qu'il est possible de créer un moteur CI/CD from scratch, fonctionnel et sécurisé, en comprenant les fondamentaux plutôt qu'en utilisant une boîte noire."

---

## 🎯 Questions probables et réponses

### Q1 : "Pourquoi ne pas utiliser Jenkins ?"
**R** : Le but pédagogique est de **comprendre** comment fonctionne un CI/CD en l'implémentant, pas de juste l'utiliser.

### Q2 : "Comment gérez-vous la concurrence ?"
**R** : Via `@Async` avec un **ThreadPoolTaskExecutor** configuré (max 5 pipelines simultanés, file d'attente de 50).

### Q3 : "Et si MongoDB tombe pendant un pipeline ?"
**R** : Le pipeline continue, mais la sauvegarde échoue. On pourrait ajouter un mécanisme de retry ou un fallback sur fichier.

### Q4 : "Le rollback est-il complet ?"
**R** : Non, **partiel**. Seules certaines étapes supportent le rollback (ex: DockerDeployStep redéploie l'ancienne version). C'est une limitation connue.

### Q5 : "Peut-on ajouter d'autres étapes ?"
**R** : Oui, facilement ! Il suffit de :
1. Créer une classe qui `extends AbstractPipelineStep`
2. Implémenter `execute()` et `rollback()`
3. L'injecter dans `PipelineService`

### Q6 : "Comment sécurisez-vous les secrets (tokens, clés SSH) ?"
**R** : Pour l'instant, ils sont passés dans la requête. En production, on utiliserait un **Vault** (HashiCorp Vault, AWS Secrets Manager).

---

## 📊 Métriques du projet

- **Lignes de code** : ~2500 lignes Java
- **Nombre de classes** : 25+
- **Étapes de pipeline** : 8
- **Endpoints API** : 7
- **Tests** : Unitaires + intégration

---

## 🚀 Commandes pour la démo

### Préparer la démo
```bash
# Démarrer MongoDB
docker run -d -p 27017:27017 --name mongodb mongo:latest

# Lancer JONK
cd jonk
mvn spring-boot:run
```

### Pendant la démo
```bash
# 1. Health check
curl http://localhost:8080/api/pipeline/health

# 2. Lancer pipeline
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Content-Type: application/json" \
  -d @examples/request-simple.json

# 3. Récupérer l'executionId dans la réponse
EXEC_ID="..."

# 4. Consulter le pipeline
curl http://localhost:8080/api/pipeline/$EXEC_ID | jq

# 5. Afficher les logs
curl http://localhost:8080/api/pipeline/$EXEC_ID/logs | jq -r '.logs[]'

# 6. Lister toutes les exécutions
curl http://localhost:8080/api/pipeline/executions | jq
```

---

**Bonne chance pour la soutenance ! 🎓🚀**
