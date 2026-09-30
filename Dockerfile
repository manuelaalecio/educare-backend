# Imagem de produção: linux/amd64 (VM x86_64 da Oracle), construída e publicada no
# GHCR pelo CI (.github/workflows/ci.yml). A imagem é pública: nada de segredos aqui;
# a configuração vem só de variáveis de ambiente em tempo de execução.

# Etapa 1: compila o projeto com Gradle
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# Etapa 2: imagem final, só com o Java e o .jar, rodando sem privilégios de root
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
