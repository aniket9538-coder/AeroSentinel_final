# ==========================================
# Stage 1: Build
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy Maven wrapper configuration and project descriptor
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings and set execution permissions
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Pre-fetch dependencies to leverage Docker layer caching
RUN ./mvnw dependency:resolve -B

# Copy project source tree
COPY src/ src/

# Compile and package production Spring Boot executable JAR (skipping test execution)
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Stage 2: Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Create a dedicated non-root application user and group for security
RUN groupadd -r aerosentinel && useradd -r -g aerosentinel -d /app -s /sbin/nologin aerosentinel

# Copy the packaged Spring Boot JAR from the build stage
COPY --from=build --chown=aerosentinel:aerosentinel /app/target/aerosentinel-backend-1.0.0.jar app.jar

# Switch to the unprivileged user
USER aerosentinel

# Expose default application port
EXPOSE 8080

# Environment defaults for production deployment
# Spring Boot automatically binds to 0.0.0.0 and maps ${PORT:8080}
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Launch Spring Boot; respects Render-assigned $PORT dynamically via application.yml server.port: ${PORT:8080}
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.address=0.0.0.0 -jar app.jar"]
