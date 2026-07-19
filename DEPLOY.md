# Deploying Catan (JPro) on Dokploy

The real JavaFX game is served in the browser by JPro. This documents the container
and the Dokploy deploy. The Docker/build files are committed; the actual deploy + DNS
are done from the Dokploy console when ready.

## What runs

- `Dockerfile` builds an Ubuntu Temurin 25 image (JPro's bundled JavaFX 26 needs JDK 24+),
  installs the GTK/X11 libraries JavaFX needs to render headless, plus fonts, then serves
  the app with `mvn jpro:run` on port 8080 (headless, no browser auto-open).
- Traefik on Dokploy terminates TLS in front; the container only speaks plain HTTP on 8080.

## Local container test (optional, before deploying)

```
docker build -t catan-jpro .
docker run --rm -p 8080:8080 catan-jpro
```

Then open http://localhost:8080. First start is slow (JPro downloads its server
components on the first `jpro:run`).

## Dokploy deploy (from the console)

1. In Dokploy, create an Application from the GitHub repo `patr7257/CatanBoardGame`,
   Dockerfile build, exposed port `8080`.
2. Add the domain `catan.patrickrobel.dk`; Traefik issues a Lets Encrypt certificate.
3. Create the DNS A record: `catan.patrickrobel.dk -> 178.104.231.9`.
4. Deploy. Watch memory with `ssh todolist-vps` then `docker stats` (see RAM note below).
5. Once live, relabel/retire the TypeScript `/catan` on patrickrobelweb so there is one
   canonical Catan (per CatanBoardGame issue #2).

## Open items to confirm

- **JPro licensing.** The free-tier concurrent-session limits are unconfirmed (their
  pricing page did not render for automated fetch). Check https://www.jpro.one/prices
  in a browser before relying on public hosting. JPro's free tier historically shows a
  small link/branding and caps concurrent sessions.
- **RAM.** Each browser session is its own JavaFX JVM (~250-400MB). The 4GB CX23 already
  runs the todolist server, so it holds only a few concurrent players. Options: cap JPro's
  max concurrent sessions (JPro server config; default is unlimited / `-1`), and/or size the
  VPS up (Hetzner CX32 8GB / CX42 16GB), especially if the website also moves to Dokploy.
- **Fonts.** The image uses Liberation/Carlito as stand-ins for Arial/Georgia. For the exact
  Microsoft fonts, add to the Dockerfile (enable `contrib`/`multiverse` and accept the EULA):
  ```
  RUN echo "ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true" \
        | debconf-set-selections \
      && apt-get update && apt-get install -y --no-install-recommends ttf-mscorefonts-installer \
      && rm -rf /var/lib/apt/lists/*
  ```
- **Leaner image (later).** Swap the `jpro:run` CMD for a packaged `mvn jpro:release` bundle
  in a multi-stage build so the runtime image does not carry Maven + sources.
