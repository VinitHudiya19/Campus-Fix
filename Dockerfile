# Two stages: the first has Maven and the whole JDK, the second has neither.
# Building in one stage would ship a ~700 MB image containing the compiler, the
# source, and the entire Maven cache — none of which is needed to run a jar.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved before the source is copied, so this layer is
# reused on every rebuild where pom.xml has not changed. Copying everything at
# once would re-download the world after each edit to a Java file.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Maven is used directly rather than ./mvnw: the wrapper script is committed
# from Windows with CRLF line endings, which a Linux shell refuses to run.


FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Runs as a normal user. A container process running as root that is broken
# into is root inside the container, and that is a much shorter step to the
# host than it ought to be.
RUN addgroup -S campusfix && adduser -S campusfix -G campusfix

COPY --from=build /build/target/*.jar app.jar

# Uploads live here, on a mounted volume. Inside the image they would vanish
# every time the container is replaced.
RUN mkdir -p /app/uploads && chown -R campusfix:campusfix /app

USER campusfix
EXPOSE 8080

ENV STORAGE_LOCATION=/app/uploads

# The JVM's default heap is a fraction of visible RAM, which inside a container
# is the host's RAM. Without this it happily sizes a heap the container is not
# allowed to use and gets killed.
#
# 60% rather than 75%: on a 512 MB instance — Render's free tier — 75% leaves
# only ~128 MB for metaspace, thread stacks and native memory, and Spring Boot's
# metaspace alone is close to that. Override JAVA_OPTS on a larger instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
