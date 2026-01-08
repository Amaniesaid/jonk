# Étape de build avec Maven
FROM maven:3.9.6-amazoncorretto-21 as build

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
FROM amazoncorretto:21.0.5-alpine3.20

# Copier le fichier .jar généré à partir de l'étape précédente
COPY --from=build /api/target/*.jar app.jar

# Exposer le port utilisé par Spring Boot
EXPOSE 8080/tcp

# Commande d'entrée pour démarrer l'application
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "/app.jar"]
