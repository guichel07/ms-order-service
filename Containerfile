# Containerfile
FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

COPY build/quarkus-app/lib/ /app/lib/
COPY build/quarkus-app/*.jar /app/
COPY build/quarkus-app/app/ /app/app/
COPY build/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080
USER 185
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
