FROM eclipse-temurin:26-jdk AS builder

ARG MAVEN_VERSION=3.9.15
RUN apt-get update -q && \
    apt-get install -yq --no-install-recommends curl && \
    curl -fsSL "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
         | tar -xzf - -C /opt && \
    ln -sf "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY adapters/pom.xml adapters/
COPY test-support/pom.xml test-support/
COPY tools/terminal-simulator/pom.xml tools/terminal-simulator/
COPY services/acquiring-service/pom.xml services/acquiring-service/
COPY services/chargeback-service/pom.xml services/chargeback-service/
COPY services/merchant-simulator/pom.xml services/merchant-simulator/
COPY services/mock-upstream/pom.xml services/mock-upstream/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/payment-switch/pom.xml services/payment-switch/
COPY services/qa-orchestrator/pom.xml services/qa-orchestrator/
COPY services/reconciliation-service/pom.xml services/reconciliation-service/
COPY services/settlement-service/pom.xml services/settlement-service/
COPY services/webhook-dispatcher/pom.xml services/webhook-dispatcher/
RUN mvn dependency:go-offline -q

COPY domain/src domain/src
COPY application/src application/src
COPY adapters/src adapters/src
COPY services/webhook-dispatcher/src services/webhook-dispatcher/src
RUN mvn package -DskipTests -pl services/webhook-dispatcher -am -q

FROM eclipse-temurin:26-jdk AS runtime
WORKDIR /app
RUN apt-get update -q && \
    apt-get install -yq --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system payments && \
    useradd --system --gid payments --no-create-home --shell /bin/false payments
USER payments

COPY --from=builder /build/services/webhook-dispatcher/target/*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 \
                       -XX:+UseContainerSupport \
                       -XX:+ExitOnOutOfMemoryError \
                       -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -sf http://localhost:8086/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
