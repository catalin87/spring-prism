FROM node:22-alpine AS ui-build
WORKDIR /workspace/prism-examples/demo-app/ui
COPY prism-examples/demo-app/ui/package.json ./
RUN npm install
COPY prism-examples/demo-app/ui ./
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS model-download
RUN apk add --no-cache curl && mkdir -p /opt/spring-prism/models /tmp/opennlp \
    && curl -fsSL https://downloads.sourceforge.net/project/opennlp/models-1.5/en-ner-person.bin \
      -o /opt/spring-prism/models/en-ner-person.bin

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
COPY --from=ui-build /workspace/prism-examples/demo-app/src/main/resources/static/lab ./prism-examples/demo-app/src/main/resources/static/lab
RUN mvn -q -DskipTests -Djacoco.skip=true install
RUN mvn -q -DskipTests -Djacoco.skip=true -f prism-examples/demo-app/pom.xml package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=model-download /opt/spring-prism/models /opt/spring-prism/models
COPY --from=build /workspace/prism-examples/demo-app/target/demo-app-1.1.0.jar /app/demo-app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/demo-app.jar"]
