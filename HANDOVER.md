# HANDOVER

## Date, branch, PR, CI
- 2026-07-21. Branch: `main` (all feature branches merged and deleted).
- CatanBoardGame PRs this session, all MERGED: #5 (deploy prep: app-level cap + centralised
  player range + lean `jpro:release` Dockerfile), #7 (perf), #9 (session reaping). Plus docs
  commit `82c23fd`. Related: patrickrobelweb PR #119 (removed the TypeScript Catan) MERGED.
- CI: none configured. Gates used: `mvn test` 10/10 green, local Docker release image builds +
  serves, and Dokploy auto-deploy on push to `main`.

## TLDR of session outcome
- DONE: the real JavaFX Catan is LIVE in the browser at `https://catan.patrickrobel.dk`
  (Dokploy, built from `Dockerfile` as a two-stage `jpro:release` image on Temurin 25).
- DONE: Dokploy panel now at `https://deploy.patrickrobel.dk` (Lets Encrypt); the Dokploy
  GitHub App URLs were repointed to that domain so push auto-deploy keeps working.
- DONE: hard 1-game cap in the app (`GameConfig.MAX_CONCURRENT_GAMES` + `ConcurrentGameLimiter`,
  enforced in `CatanBoardGameApp`), with `src/main/resources/jpro.conf` reaping abandoned
  sessions (`closeOnDisconnectAfter = 30`) so the slot frees quickly; a refresh rejoins the game.
- DONE: perf pass to reduce JPro re-render cost (hover-move guard, debug discs off, AI image
  built-once + downscaled, dice/log). Remaining perf levers tracked in issue #6.
- DONE: removed the TypeScript Catan from patrickrobelweb (engine, online API, components,
  routes) and repointed the arcade card + portfolio link to `catan.patrickrobel.dk`.
- DONE: rewrote CatanBoardGame issue #4 as research for a Java headless self-play "Pro" AI.

## Prioritized next steps
1. Live-verify on `catan.patrickrobel.dk` (was not confirmed yet): felt speed (hover/click), and
   the slot behaviour (close a tab, within ~30s a fresh visit can start; a quick refresh rejoins).
2. If still sluggish, work issue #6's remaining levers (sidebar in-place update instead of full
   rebuild; pan/zoom transform cost) and/or size the VPS up to 4 vCPU (Hetzner CX32).
3. Confirm the patrickrobelweb prod deploy: the arcade Catan card opens `catan.patrickrobel.dk`.
4. Optionally start issue #4 (Pro AI): the make-or-break spike is a headless, deterministic,
   sleep-free all-AI game runner (target 1000+ games/min), then tune heuristic weights by
   self-play win-rate.

## Verbatim resume commands (PowerShell)
Run the tests:
```
cd "C:\Users\pr\repos\3-Studie\CatanBoardGame"; mvn test
```
Serve it locally in a browser (JDK 25 for JPro), then open http://localhost:8080:
```
cd "C:\Users\pr\repos\3-Studie\CatanBoardGame"; $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"; mvn jpro:run
```
Build + run the production release image locally (Docker Desktop must be started first):
```
cd "C:\Users\pr\repos\3-Studie\CatanBoardGame"; docker build -t catan-jpro . ; docker run --rm -p 8080:8080 catan-jpro
```

## Gotchas discovered this session
- The 1-game cap can ONLY be enforced in the app. JPro's `one.jpro.loadbalancer.sessionsPerServer`
  needs the separate loadbalancer component (not in the release bundle, ignored by a single
  server and by `jpro:run` dev mode), and a Traefik `inFlightReq` limit breaks a WebSocket app
  (it would block a single player's own asset requests). Hence `ConcurrentGameLimiter`.
- `jpro.conf` is HOCON: fields are newline- or comma-separated, NEVER `;`. A semicolon crashes
  the server on startup with `ConfigException$Parse`. JPro loads it from the classpath
  (`src/main/resources/jpro.conf`), confirmed in the release bundle.
- The release runs on the CLASSPATH (`bin/start.sh` uses `-cp`), so `module-info.java` is
  compile-time only; `requires jpro.webapi` is needed to COMPILE the WebAPI use, and it resolves
  fine at runtime on the classpath.
- `jpro:release` bundles JavaFX for all platforms; JPro's JavaFX `26.0.2-jpro+4` is bytecode 68
  (Java 24), so the runtime JDK must be 24+ (we use Temurin 25). The bundle's own generated
  Dockerfile uses Temurin 21 (would fail); our `Dockerfile` overrides it.
- Dokploy auto-deploys on EVERY push to `main` (Watch Paths empty), so even a docs-only push
  triggers a rebuild + brief container restart of the live game.
- Docker Desktop is at a non-standard path on this machine; auto-launch + poll routine is in the
  session memory (docker-desktop-autolaunch).

## Open decisions waiting on Patrick
- Is the perf pass enough, or bump the VPS to 4 vCPU (CX32) for smoother rendering?
- Tackle issue #4 (Pro AI headless self-play) next, or leave it in Backlog?
- Ship the exact Microsoft fonts (msttcorefonts EULA) or keep the Liberation/Carlito stand-ins?
  (see `DEPLOY.md`).

## Environment state
- Local: both repos on `main`, no worktrees, no dev servers. Docker Desktop STOPPED; the three
  local `catan-jpro` test images were removed. Port 8080 free.
- Prod (leave running): `catan.patrickrobel.dk` (Dokploy catan app) and `deploy.patrickrobel.dk`
  (Dokploy panel) are live on the Hetzner VPS `178.104.231.9`.
