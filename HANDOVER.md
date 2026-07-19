# HANDOVER

## Date, branch, PR, CI
- 2026-07-19. Branch: `main` (feature branch `feat/jpro-web-port` merged and deleted).
- PR #3 (https://github.com/patr7257/CatanBoardGame/pull/3): MERGED to main as `19df1eb`.
- CI: none configured in this repo. Local `mvn test` green on JDK 21 and JDK 25 (7/7).

## TLDR of session outcome
- DONE: the real desktop JavaFX Catan now plays in a browser via JPro (no rewrite). Input is
  accurate at any zoom/pan; every popup works as a non-blocking in-scene overlay.
- DONE: Phase A (JPro integration on JDK 25, resource/case fixes, repaired test suite),
  Phase B (single-Scene + origin-pivot zoom input fix, full dialog refactor). All merged.
- PREPPED (not executed): Phase C hosting. `Dockerfile`, `.dockerignore`, `DEPLOY.md` are on main.
- The next session is Phase C: deploy to Dokploy at `catan.patrickrobel.dk`.

## Prioritized next steps
1. Local Docker build test: `docker build -t catan-jpro .` then run and open http://localhost:8080.
2. Confirm JPro free-tier limits at https://www.jpro.one/prices (concurrent sessions / branding).
3. Decide VPS sizing: cap JPro sessions and/or upgrade the 4GB CX23 (each session is its own ~250-400MB JVM).
4. In Dokploy: create an App from repo `patr7257/CatanBoardGame` (Dockerfile build, port 8080).
5. Add domain `catan.patrickrobel.dk` (Traefik + Lets Encrypt) and DNS A record -> 178.104.231.9.
6. Deploy; watch `docker stats` over `ssh todolist-vps`.
7. Once live, retire/relabel the TypeScript `/catan` on patrickrobelweb so there is one canonical Catan.

## Verbatim resume commands (PowerShell)
Run the tests:
```
cd "C:\Users\pr\repos\3-Studie\CatanBoardGame"; mvn test
```
Serve it in a browser locally (JDK 25 required for JPro), then open http://localhost:8080:
```
cd "C:\Users\pr\repos\3-Studie\CatanBoardGame"; $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"; mvn jpro:run
```
Build the Docker image locally to test the container:
```
cd "C:\Users\pr\repos\3-Studie\CatanBoardGame"; docker build -t catan-jpro .
```

## Gotchas discovered this session
- JPro 2026.3.0 injects its own patched JavaFX 26 (bytecode 68 = Java 24), so the JPro run/deploy
  JDK must be 24+. We use Temurin 25 LTS (installed at `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`).
  Desktop build + tests stay on Java 21. You CANNOT pin a plain `javafxVersion` (vanilla JavaFX lacks
  JPro's `Platform.overwriteIsSupported`); JPro's patched fork is mandatory.
- The `mvn jpro:run` dev server dies between turns if launched with `nohup ... &`. Launch it detached:
  `Start-Process cmd -ArgumentList "/c mvn jpro:run > log 2>&1" -WindowStyle Hidden`.
- Input fix (already merged): keep ONE Scene and swap its root (never `stage.setScene` at runtime, it
  breaks JPro input). Zoom uses an explicit origin-pivot `Scale` transform, NOT `setScaleX/Y` (whose
  bounds-center pivot resolves wrong under JPro's headless renderer and offsets clicks). Board clicks are
  hit-tested in scene space (DrawOrDisplay dispatchBoardClick/Hover), not JPro node-picking.
- Dialogs: JPro cannot block the FX thread; all popups are in-scene overlays via CatanBoardGameView
  showOverlay/showInfoOverlay/showConfirmOverlay/showChoiceOverlay.
- Tests on JDK 25: Mockito/ByteBuddy needs the Surefire `argLine -Dnet.bytebuddy.experimental=true`
  (already in pom). The hex math (Board.axialToPixel) was NOT the bug and is correct.

## Open decisions waiting on Patrick
- JPro free-tier vs paid: is the free tier's session cap / branding acceptable for public hosting? (check prices page)
- VPS: cap sessions on the existing 4GB CX23, or upgrade (CX32 8GB / CX42 16GB)? Ties to whether the
  website also moves off Vercel to Dokploy.
- Fonts: ship exact Microsoft fonts (msttcorefonts, EULA) or keep the Liberation/Carlito stand-ins? (DEPLOY.md)

## Environment state
- JPro dev server STOPPED; port 8080 free. No Docker containers running. No extra git worktrees.
- Merged branch `feat/jpro-web-port` deleted (local + remote). `main` is at `19df1eb`.
- Issue #2 closed (merged via PR #3).
