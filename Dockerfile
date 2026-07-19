# Real JavaFX Catan, served in the browser via JPro.
#
# Base: Ubuntu-based Temurin 25. JPro 2026.3.0 injects its own patched JavaFX 26
# (bytecode 68 = Java 24), so the runtime must be JDK 24+; we use JDK 25 LTS.
# JavaFX needs GTK/X11 libraries to render even in JPro's headless Monocle mode,
# plus fonts for the UI. Traefik on Dokploy terminates TLS in front of this.
FROM maven:3.9-eclipse-temurin-25

# JavaFX headless rendering dependencies + fonts. Liberation/Carlito stand in for
# Arial/Georgia; see DEPLOY.md for installing the real Microsoft fonts if exact
# fidelity is wanted.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgtk-3-0 libglib2.0-0 libgl1 libxtst6 libxrender1 libxi6 libxxf86vm1 \
        fontconfig fonts-liberation fonts-liberation2 fonts-crosextra-carlito \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Pre-resolve Maven dependencies first for faster image rebuilds. (JPro's own server
# components download on the first jpro:run; that first start is therefore slower.)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline -DskipTests || true

# Build the app.
COPY src ./src
RUN mvn -q -B -DskipTests clean compile

EXPOSE 8080

# Serve headless: do not open a browser, bind JPro on port 8080. JPro already runs
# its JavaFX renderer in headless Monocle mode.
#
# NOTE: this uses the jpro:run goal (proven, and what we run locally). For a leaner
# production image, switch to a packaged `mvn jpro:release` bundle later (see DEPLOY.md).
CMD ["mvn", "-B", "jpro:run", "-Djpro.openURLOnStartup=false", "-Djpro.port=8080"]
