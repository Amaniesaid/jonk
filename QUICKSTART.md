# 🚀 Guide de Démarrage Rapide - JONK CI/CD Engine

## 📋 Prérequis

Avant de commencer, assurez-vous d'avoir installé :

```bash
# Vérifier Java 21
java --version
# Devrait afficher: openjdk version "21" ou supérieur

# Vérifier Maven
mvn --version
# Devrait afficher: Apache Maven 3.8+ ou supérieur

# Vérifier Docker
docker --version
# Devrait afficher: Docker version 20+ ou supérieur

# Vérifier Git
git --version
```

## 🔧 Installation

### 1. Démarrer MongoDB

**Option A : Avec Docker (Recommandé)**
```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -v mongodb_data:/data/db \
  mongo:latest

# Vérifier que MongoDB fonctionne
docker ps | grep mongodb
```

**Option B : MongoDB local**
```bash
# Sur macOS avec Homebrew
brew services start mongodb-community

# Sur Linux
sudo systemctl start mongod
```

### 2. Cloner et compiler le projet

```bash
# Cloner le projet (si ce n'est pas déjà fait)
cd jonk

# Compiler le projet
mvn clean install -DskipTests

# Vérifier que la compilation réussit
# Vous devriez voir : BUILD SUCCESS
```

### 3. Lancer l'application

```bash
# Démarrer JONK
mvn spring-boot:run

# Ou en utilisant le JAR
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

L'application démarre sur **http://localhost:8080**

Vous devriez voir dans les logs :
```
🛠️ Configuration de l'executor asynchrone pour les pipelines
   - Core pool size: 2
   - Max pool size: 5
   - Queue capacity: 50

Started JonkBackApplication in X.XXX seconds
```

## ✅ Vérifier que tout fonctionne

### Test 1 : Health Check
```bash
curl http://localhost:8080/api/pipeline/health
```

**Réponse attendue :**
```json
{
  "status": "UP",
  "service": "Jonk CI/CD Engine",
  "version": "1.0.0"
}
```

### Test 2 : Lancer un pipeline simple
```bash
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Content-Type: application/json" \
  -d '{
    "gitUrl": "https://github.com/spring-projects/spring-petclinic.git",
    "branch": "main",
    "dockerImageName": "petclinic-test",
    "dockerImageTag": "v1.0.0",
    "deploymentPort": "8081",
    "triggeredBy": "test-user"
  }'
```

**Réponse attendue :**
```json
{
  "executionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Pipeline démarré avec succès",
  "status": "RUNNING"
}
```

### Test 3 : Suivre le pipeline

Copiez l'`executionId` reçu et exécutez :

```bash
# Remplacez <execution_id> par l'ID reçu
EXEC_ID="a1b2c3d4-e5f6-7890-abcd-ef1234567890"

# Consulter le statut
curl http://localhost:8080/api/pipeline/$EXEC_ID

# Voir les logs
curl http://localhost:8080/api/pipeline/$EXEC_ID/logs
```

## 📊 Observer les logs

Dans le terminal où JONK s'exécute, vous verrez :

```
🚀 Démarrage du pipeline: a1b2c3d4-...
📁 Repository: https://github.com/spring-projects/spring-petclinic.git
🔀 Branche: main
═══════════════════════════════════════════════════════════
───────────────────────────────────────────────────────────
▶️  Exécution de l'étape: Git Clone
───────────────────────────────────────────────────────────
✅ Étape 'Git Clone' terminée avec succès en 2340ms
───────────────────────────────────────────────────────────
▶️  Exécution de l'étape: Maven Build
───────────────────────────────────────────────────────────
...
```

## 🎯 Exemples de requêtes

Le projet contient des exemples de requêtes dans `examples/` :

### Pipeline simple (local)
```bash
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Content-Type: application/json" \
  -d @examples/request-simple.json
```

### Pipeline complet avec SonarQube
```bash
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Content-Type: application/json" \
  -d @examples/request-complete.json
```

### Pipeline avec déploiement distant
```bash
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Content-Type: application/json" \
  -d @examples/request-remote-deploy.json
```

## 🔍 Consulter MongoDB

Pour voir les exécutions stockées dans MongoDB :

```bash
# Se connecter à MongoDB
docker exec -it mongodb mongosh

# Utiliser la base de données
use jonk-cicd

# Lister les exécutions
db.pipeline_executions.find().pretty()

# Compter les exécutions
db.pipeline_executions.count()

# Trouver les pipelines réussis
db.pipeline_executions.find({ status: "SUCCESS" }).pretty()

# Quitter
exit
```

## 🛠️ Commandes utiles

### Nettoyer et recompiler
```bash
mvn clean install
```

### Lancer les tests
```bash
mvn test
```

### Générer le JAR standalone
```bash
mvn clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

### Redémarrer MongoDB
```bash
docker restart mongodb
```

### Voir les logs MongoDB
```bash
docker logs mongodb
```

## 🐛 Résolution de problèmes

### Problème : "Connection refused" à MongoDB

**Solution :**
```bash
# Vérifier que MongoDB est en cours d'exécution
docker ps | grep mongodb

# Si absent, démarrer MongoDB
docker start mongodb
# Ou créer un nouveau container
docker run -d -p 27017:27017 --name mongodb mongo:latest
```

### Problème : Port 8080 déjà utilisé

**Solution :**
```bash
# Changer le port dans application.properties
server.port=8081

# Ou tuer le processus utilisant 8080
lsof -ti:8080 | xargs kill -9
```

### Problème : "Git clone failed"

**Solution :**
- Vérifier que `git` est installé : `git --version`
- Vérifier l'URL du repository
- Vérifier la connexion internet

### Problème : "Docker build failed"

**Solution :**
- Vérifier que Docker est démarré : `docker ps`
- Vérifier que le Dockerfile existe dans le repo cloné
- Vérifier les permissions

## 🔐 Configuration OAuth2 (optionnel)

Pour activer la sécurité OAuth2 avec Keycloak :

1. **Installer Keycloak**
```bash
docker run -d \
  --name keycloak \
  -p 8180:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

2. **Configurer dans application.properties**
```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/jonk
```

3. **Créer un realm "jonk"** avec les rôles `ADMIN`, `DEV`, `VIEWER`

4. **Utiliser un token JWT**
```bash
# Obtenir un token
TOKEN=$(curl -X POST "http://localhost:8180/realms/jonk/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=jonk-client" | jq -r '.access_token')

# Utiliser le token
curl -X POST http://localhost:8080/api/pipeline/run \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @examples/request-simple.json
```

## 📚 Documentation complète

Pour plus d'informations, consultez :
- [README.md](README.md) - Documentation complète
- [PRESENTATION.md](PRESENTATION.md) - Support de présentation
- [examples/](examples/) - Exemples de requêtes

## 🆘 Besoin d'aide ?

Si vous rencontrez des problèmes :
1. Vérifiez les logs de l'application
2. Vérifiez que MongoDB est accessible
3. Vérifiez que Docker est démarré
4. Consultez la section "Résolution de problèmes" ci-dessus

---

**Bonne utilisation de JONK ! 🚀**
