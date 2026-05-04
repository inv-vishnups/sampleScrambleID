# Build
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src ./src
RUN ./mvnw -q -DskipTests package

# Run
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -g 1001 spring && adduser -S -u 1001 -G spring spring
COPY --from=build /build/target/*.jar app.jar
USER spring:spring
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
