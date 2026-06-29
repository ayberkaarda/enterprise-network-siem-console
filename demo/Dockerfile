FROM eclipse-temurin:26-jdk-alpine AS build
WORKDIR /workspace/app

# Kök sertifikaları güncelliyoruz
RUN apk add --no-cache maven ca-certificates openssl && update-ca-certificates

COPY pom.xml .
RUN mvn dependency:go-offline -B -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true

COPY src src
RUN mvn clean package -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true

# SORUN YARATAN "jar -xf" SATIRINI TAMAMEN KALDIRDIK

FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

# JAR dosyasını doğrudan kopyalayıp çalıştırıyoruz
COPY --from=build /workspace/app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]