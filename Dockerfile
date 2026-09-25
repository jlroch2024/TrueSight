# The live site's package: one image holding the website and the backend, run on Google Cloud Run.
# Google Cloud builds it from this file on every deploy (see docs/deployment.md). It is built in three steps, and only
# the last one is kept, so the live image holds Java and the app, and nothing used only for building.

# Step 1: build the website. The result is plain files (index.html, JavaScript, CSS, images) in frontend/dist.
FROM node:24-alpine AS website
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Step 2: build the backend, with the website copied into its static folder, so the backend serves both.
# The libraries are downloaded before the code is copied, so a code-only change does not download them all again.
# The tests are skipped here because GitHub Actions has already run them, and the deploy only starts once they pass.
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=website /frontend/dist src/main/resources/static
RUN ./mvnw -B -q package -DskipTests

# Step 3: the image that actually runs. It runs as an ordinary user rather than root, in case the app is ever broken
# into. The prod profile is set here as well as on Cloud Run, so this image can never create the demo account, whose
# password is public.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system truesight
COPY --from=backend /backend/target/truesight-*.jar app.jar
USER truesight
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# Cloud Run gives the app a fixed amount of memory; this lets Java use most of it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
