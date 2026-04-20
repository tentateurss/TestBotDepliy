FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Копируем Maven wrapper и pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Скачиваем зависимости
RUN ./mvnw dependency:go-offline

# Копируем исходники
COPY src ./src

# Собираем проект
RUN ./mvnw clean package -DskipTests

# Запускаем
CMD ["java", "-jar", "target/BotTG2-1.0.0.jar"]
