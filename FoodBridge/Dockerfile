FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 foodbridge && mkdir /app/uploads && chown foodbridge /app/uploads
COPY --from=build /app/target/foodbridge-1.0.0.jar app.jar
USER foodbridge
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
