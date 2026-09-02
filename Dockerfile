# A Docker image for the Operational Insights JVM SDK FIT performers.
#
# Build from project root with:
#   docker build . --build-arg SDK=<sdk> -t performer
#
# Run with:
#   docker run -e LOG_LEVEL=DEBUG -p 8060:8060 performer

# Valid SDK values: insights-java
ARG SDK=insights-java

FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /app
COPY . couchbase-insights-jvm-clients/

WORKDIR /app/couchbase-insights-jvm-clients
ARG MVN_FLAGS="--batch-mode --no-transfer-progress -Dcheckstyle.skip -Dmaven.test.skip -Dmaven.javadoc.skip"
ARG SDK
RUN mvn $MVN_FLAGS package -Pfit --projects couchbase-${SDK}-client/fit --also-make

# Multistage build to keep things small
FROM eclipse-temurin:21-jre-ubi10-minimal

ARG SDK
COPY --from=build /app/couchbase-insights-jvm-clients/couchbase-${SDK}-client/fit/target/${SDK}-fit-performer-1.0-SNAPSHOT-jar-with-dependencies.jar performer.jar

ENV LOG_LEVEL=INFO
EXPOSE 8060

ENTRYPOINT ["java", "-jar", "performer.jar"]
