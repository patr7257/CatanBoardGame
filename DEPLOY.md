# Deploying Catan (JPro) on Dokploy

The real JavaFX game is served in the browser by JPro. This documents the container
and the Dokploy deploy. The Docker/build files are committed; the actual deploy + DNS
are done from the Dokploy console when ready.

## What runs

- `Dockerfile` is a two-stage build:
  1. `mvn jpro:release` (Maven + Temurin 25) produces a self-contained bundle
     (app + jprolibs + platform JavaFX + fonts).
  2. A lean Temurin 25 image unzips that bundle and runs it via `bin/start.sh` on port
     8080. No Maven, sources, or dependency resolution at run time.
- JPro's patched JavaFX 26 needs a JDK 24+ runtime (we use Temurin 25). JavaFX still needs
  the GTK/X11 libraries to render in headless Monocle mode; those are installed in the image.
- Traefik on Dokploy terminates TLS in front; the container only speaks plain HTTP on 8080.

## Concurrency cap: one game at a time (app-level)

The hard cap lives in the app, not the proxy or JPro config:

- `GameConfig.MAX_CONCURRENT_GAMES` (currently `1`) is the limit.
- Under JPro's single server, every browser session is its own `Application` instance in
  one JVM, so `CatanBoardGameApp` keeps a static counter: it increments on
  `start()`, decrements on `stop()`, and shows a "one game at a time, try again shortly"
  screen when the limit is reached. Raise `MAX_CONCURRENT_GAMES` (and size the VPS up) to
  allow more games.

Why app-level and not the alternatives:
- JPro's `one.jpro.loadbalancer.sessionsPerServer` cap only works with the separate JPro
  loadbalancer component (not in the release bundle), and does nothing for a single server.
- A Traefik `inFlightReq` limit cannot cleanly cap JPro sessions: each session is a
  long-lived WebSocket, so counting in-flight requests breaks even a single player.

The in-game player range (2-6) is a separate limit in `GameConfig.MIN_PLAYERS` / `MAX_PLAYERS`.

## Local container test (before deploying)

```
docker build -t catan-jpro .
docker run --rm -p 8080:8080 catan-jpro
```

Then open http://localhost:8080. The release image starts faster than dev mode (no server
components to download at run time).

Cap check: with a game running in one tab, open a SECOND browser tab to
http://localhost:8080. The second tab must show the "one game at a time" screen, not a
second playable game. (This is enforced by the app, so it holds in the container and after
deploy.)

## Dokploy deploy (from the console)

1. In Dokploy, create an Application from the GitHub repo `patr7257/CatanBoardGame`,
   Dockerfile build, exposed port `8080`.
2. Add the domain `catan.patrickrobel.dk`; Traefik issues a Lets Encrypt certificate.
3. Create the DNS A record: `catan.patrickrobel.dk -> 178.104.231.9`.
4. Deploy. Watch memory with `ssh todolist-vps` then `docker stats` (see RAM note below).
5. Once live, relabel/retire the TypeScript `/catan` on patrickrobelweb so there is one
   canonical Catan (per CatanBoardGame issue #1).

## Open items to confirm

- **JPro licensing.** The free-tier limits are unconfirmed (their pricing page is a JS SPA
  that did not render for automated fetch). Check https://www.jpro.one/prices in a browser
  before relying on public hosting. JPro's free tier historically shows a small link/branding
  and caps concurrent sessions; a 1-game deployment is well within any such cap.
- **RAM.** The release runs a single JVM serving the one allowed game, alongside the todolist
  server on the 4GB CX23. Watch `docker stats` after deploy; raise `MAX_CONCURRENT_GAMES` only
  together with a bigger VPS (Hetzner CX32 8GB / CX42 16GB).
- **Fonts.** The image uses the bundle's Lato/Roboto plus Liberation/Carlito stand-ins for
  Arial/Georgia. For the exact Microsoft fonts, add to the runtime stage of the Dockerfile
  (enable `contrib`/`multiverse` and accept the EULA):
  ```
  RUN echo "ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true" \
        | debconf-set-selections \
      && apt-get update && apt-get install -y --no-install-recommends ttf-mscorefonts-installer \
      && rm -rf /var/lib/apt/lists/*
  ```
