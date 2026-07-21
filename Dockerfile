# Real JavaFX Catan, served in the browser via JPro, built as a production release.
#
# Two stages:
#   1. build  - Maven + Temurin 25 runs `mvn jpro:release`, producing a self-contained
#               bundle (app + jprolibs + platform JavaFX + fonts) as a zip. Building on
#               Linux ensures the bundle's libs/jfx are linux-native.
#   2. runtime- a lean Temurin 25 image that just unzips the bundle and runs it. No Maven,
#               no sources, no dependency resolution at run time.
#
# JPro 2026.3.0 ships patched JavaFX 26 (bytecode 68 = Java 24), so the runtime JDK must be
# 24+; we use Temurin 25 LTS. JavaFX still needs GTK/X11 libs to render in headless Monocle
# mode. Traefik on Dokploy terminates TLS in front; this container speaks plain HTTP on 8080.
#
# The concurrent-game cap (1 game) is enforced in the app itself
# (GameConfig.MAX_CONCURRENT_GAMES + CatanBoardGameApp), not here or in the proxy. See DEPLOY.md.

# ---- Stage 1: build the JPro release bundle ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Pre-resolve Maven dependencies first for faster image rebuilds.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline -DskipTests || true

# Build the release bundle: target/CatanBoardGameApp-jpro.zip
COPY src ./src
RUN mvn -q -B -DskipTests jpro:release

# ---- Stage 2: lean runtime ----
FROM eclipse-temurin:25-jdk

# JavaFX headless rendering dependencies + unzip. Fonts come from the bundle's own fonts/
# dir (Lato/Roboto via JPro's logical-font mapping); Liberation/Carlito are installed as
# extra system stand-ins for Arial/Georgia (see DEPLOY.md for exact Microsoft fonts).
RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip \
        libgtk-3-0 libglib2.0-0 libgl1 libxtst6 libxrender1 libxi6 libxxf86vm1 \
        fontconfig fonts-liberation fonts-liberation2 fonts-crosextra-carlito \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/CatanBoardGameApp-jpro.zip /tmp/app.zip
RUN unzip -q /tmp/app.zip -d /opt \
    && rm /tmp/app.zip \
    && chmod +x /opt/CatanBoardGameApp-jpro/bin/*.sh

WORKDIR /opt/CatanBoardGameApp-jpro
EXPOSE 8080

# start.sh runs a single JProBoot server in production mode; the bundled vmoptions bind
# -Dhttp.port=8080 and headless Monocle. It runs in the foreground as the container process.
CMD ["bash", "bin/start.sh"]
