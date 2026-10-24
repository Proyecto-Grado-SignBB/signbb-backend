# =============================================================================
# Imagen del backend de SignBB (1.4)
# =============================================================================
# Dos etapas: la primera compila el jar con el wrapper de Maven y la segunda
# solo lleva el JRE, curl (para el healthcheck) y el jar, con un usuario sin
# privilegios. Las pruebas no corren aquí: las corre el CI con PostgreSQL y
# Redis reales.
#
# Se construye desde contenedores/compose.yaml (servicio "backend") o a mano:
#   docker build -t signbb-backend backend
# =============================================================================

# --- Etapa 1: compilación ----------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /fuente

# Primero el wrapper y el pom: si solo cambia el código, Docker reutiliza esta
# capa. El caché de BuildKit guarda ~/.m2 entre construcciones.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests package \
    && cp target/backend-*.jar /fuente/app.jar

# --- Etapa 2: ejecución ------------------------------------------------------
FROM eclipse-temurin:25-jre

# curl: lo usa el healthcheck del compose contra /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuario sin root: si alguien compromete la app, no es root del contenedor.
RUN groupadd --system signbb && useradd --system --gid signbb --no-create-home signbb
WORKDIR /app
COPY --from=build --chown=signbb:signbb /fuente/app.jar app.jar
USER signbb

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
