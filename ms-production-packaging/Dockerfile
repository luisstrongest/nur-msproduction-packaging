# =============================================================================
# ms-production-packaging (BC4 - Produccion y Envasado)
#
# Build multi-stage: separa la descarga de dependencias del compilado para que
# Docker pueda cachear la capa de dependencias (la mas lenta) y solo recompile
# cuando cambia el codigo fuente.
#
# Build:
#   docker build -t luisstrongest/ms-production-packaging:1.0.0 .
# =============================================================================


# =============================================================================
# STAGE 1: "dependency-cache"
#   Descarga todas las dependencias Maven al repositorio local del contenedor.
#   Solo se invalida si cambia pom.xml -> los rebuilds de codigo son rapidos.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS dependency-cache

WORKDIR /build

# Solo el pom: si el codigo cambia pero el pom no, esta capa se reutiliza.
COPY pom.xml .

RUN mvn dependency:go-offline -B -e


# =============================================================================
# STAGE 2: "builder"
#   Compila y empaqueta el JAR ejecutable (Spring Boot fat-jar).
# =============================================================================
FROM dependency-cache AS builder

COPY src src

# -DskipTests -> los tests corren en el pipeline de CI, no en el build de imagen.
RUN mvn package -B -e -DskipTests


# =============================================================================
# STAGE 3: "runtime"
#   Imagen final: solo JRE + el JAR. Sin Maven, sin JDK, sin codigo fuente.
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario sin privilegios: si alguien compromete la app, no es root.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder \
    --chown=appuser:appgroup \
    /build/target/ms-production-packaging-0.0.1-SNAPSHOT.jar \
    app.jar

USER appuser

# Puerto interno definido en src/main/resources/application.yml (server.port)
EXPOSE 8082

# -- JVM tuning para containers ----------------------------------------------
#
# UseContainerSupport  -> la JVM lee los cgroups del container para saber
#                         cuantos CPUs y cuanta RAM tiene realmente asignados.
# MaxRAMPercentage=75  -> usa el 75% de la RAM del container para el heap; el
#                         25% restante cubre stacks de threads, metaspace,
#                         off-heap del GC y buffers NIO.
# java.security.egd    -> acelera el arranque de Tomcat en entornos sin buena
#                         fuente de entropia (tipico en VMs y containers).
# Zona horaria de Bolivia (-04:00) para la JVM y los logs; el compose puede cambiarla
ENV TZ=America/La_Paz
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
