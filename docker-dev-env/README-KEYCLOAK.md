# 🚀 Keycloak + PostgreSQL - Guide de démarrage

## 📋 Architecture

- **PostgreSQL 16** : Base de données persistante pour Keycloak (port 5433)
- **Keycloak 25.0.2** : Serveur d'authentification (port 8180)
- **Realm Jonk** : Importé automatiquement au démarrage

## 🎯 Démarrage rapide

### 1. Démarrer l'environnement

```bash
cd docker-dev-env
docker-compose up -d
```

### 2. Vérifier le déploiement

```bash
# Logs Keycloak
docker-compose logs -f keycloak

# Logs PostgreSQL
docker-compose logs -f postgres

# Statut des services
docker-compose ps
```

### 3. Accès Keycloak

- **URL** : http://localhost:8180
- **Console Admin** : http://localhost:8180/admin
- **Username** : `admin`
- **Password** : `admin`

## 👥 Utilisateurs préconfigurés

| Username | Password | Rôles | Email |
|----------|----------|-------|-------|
| `ousmane` | `password` | ROLE_ADMIN, ROLE_DEV | ouz@gmail.com |
| `dev` | `devpass` | ROLE_DEV | dev@jonk.com |
| `viewer` | `viewerpass` | ROLE_VIEWER | viewer@jonk.com |

## 🔑 Configuration Client

- **Client ID** : `jonk-back`
- **Client Secret** : `jonk-secret`
- **Grant Types** : `password`, `authorization_code`
- **Token Lifespan** : 300s (5 minutes)

## 🧪 Test d'authentification

### Obtenir un token

```bash
curl -X POST http://localhost:8180/realms/Jonk/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=jonk-back" \
  -d "client_secret=jonk-secret" \
  -d "grant_type=password" \
  -d "username=ousmane" \
  -d "password=password"
```

### Décoder le token

Copiez l'`access_token` et décodez-le sur [jwt.io](https://jwt.io) pour vérifier les rôles.

## 🛠️ Commandes utiles

### Arrêter les services

```bash
docker-compose down
```

### Arrêter et supprimer les données

```bash
docker-compose down -v
```

### Redémarrer Keycloak seul

```bash
docker-compose restart keycloak
```

### Reconstruire après modification du realm

```bash
docker-compose down
docker volume rm docker-dev-env_postgres_data  # Supprime les données
docker-compose up -d
```

## 📁 Structure des fichiers

```
docker-dev-env/
├── docker-compose.yml          # Configuration Docker
├── realm-import/
│   └── jonk-realm.json        # Configuration du realm Jonk
└── README-KEYCLOAK.md         # Ce fichier
```

## 🔧 Personnalisation

### Modifier les utilisateurs

Éditez `realm-import/jonk-realm.json` section `users`, puis redémarrez :

```bash
docker-compose down -v
docker-compose up -d
```

### Ajouter des rôles

Dans `jonk-realm.json`, section `roles.client.jonk-back` :

```json
{
  "name": "ROLE_CUSTOM",
  "description": "Rôle personnalisé"
}
```

## 🐛 Troubleshooting

### Le realm n'est pas importé

- Vérifiez que `realm-import/jonk-realm.json` existe
- Vérifiez les logs : `docker-compose logs keycloak`
- Supprimez les volumes et redémarrez : `docker-compose down -v && docker-compose up -d`

### Erreur de connexion PostgreSQL

- Vérifiez que PostgreSQL est démarré : `docker-compose ps postgres`
- Attendez que le healthcheck passe : `docker-compose logs postgres`

### Port 5433 ou 8180 déjà utilisé

Modifiez les ports dans `docker-compose.yml` :
- PostgreSQL : `"XXXX:5432"`
- Keycloak : `"YYYY:8080"`

## 📖 Documentation

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
