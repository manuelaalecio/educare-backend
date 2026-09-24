# As imagens Temurin são multi-arquitetura (amd64 e arm64). Para a VM Ampere A1
# da Oracle (ARM64), gere a imagem na própria VM ou com --platform linux/arm64.

# Etapa 1: compila o projeto com Gradle
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# Etapa 2: imagem final, só com o Java e o .jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]