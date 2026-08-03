# MINDMAP//84 — install & GitHub auto-update

There are two related pieces, both already coded:

- **Installer** (`Installer.exe`, or `MINDMAP84.exe --install`) — a small wizard
  that copies the app into a folder you choose and makes a desktop shortcut.
- **Updater** (the **⟳ Check for Updates** button in the app, or
  `MINDMAP84.exe --update`) — pulls new versions from GitHub Releases.

Your saved data lives in `%USERPROFILE%\.mindmap84\` and is never touched by
installing or updating.

---

## One-time setup (when your GitHub repo exists)

Edit **`src/mindmap84/Config.java`**:

```java
static final String GITHUB_OWNER = "your-github-username";
static final String GITHUB_REPO  = "MINDMAP84";
```

That's the only wiring the code needs. `ASSET_NAME` already matches the file the
build produces (`MINDMAP84-windows.zip`).

---

## Publishing a new version

1. **Bump the version** in two places (keep them equal):
   - `Config.VERSION` (e.g. `1.1.0`)
   - `--app-version` in `build.ps1` / `build.sh`
2. **Build:** run `build.ps1` (PowerShell) or `build.sh` (Git Bash).
   → produces `dist\MINDMAP84\` and, after you zip it, `MINDMAP84-windows.zip`.
   (The build already re-creates the folder; zip it with the folder inside —
   `Compress-Archive dist\MINDMAP84 MINDMAP84-windows.zip`.)
3. **On GitHub:** create a **Release**
   - **Tag:** `v1.1.0`  ← must be `v` + `Config.VERSION`
   - **Attach asset:** `MINDMAP84-windows.zip`
   - Publish.

That's it. Anyone running an older copy who clicks **⟳ Check for Updates** (or
launches with `--update`) will be offered v1.1.0, download it, and the app will
restart on the new version.

---

## How the auto-update actually works

1. App calls `https://api.github.com/repos/OWNER/REPO/releases/latest`.
2. Compares the release tag (`v1.1.0` → `1.1.0`) against `Config.VERSION`.
3. If newer, downloads the `MINDMAP84-windows.zip` asset to a temp folder and
   unzips it.
4. Writes a tiny detached `.bat` that waits ~2s for the app to exit, `robocopy`s
   the new files over the install folder (this is why a helper script is needed
   — a running Java app can't overwrite its own locked runtime), then relaunches
   `MINDMAP84.exe`.
5. The app calls `System.exit(0)`, the script finishes the swap, and the new
   version starts.

The zip **must** contain the top-level `MINDMAP84\` folder (the default when you
`Compress-Archive` the folder) — the updater looks inside it for `MINDMAP84.exe`.

---

## Notes / options

- Until `Config.GITHUB_OWNER` is set, **⟳ Check for Updates** simply reports
  "GitHub repo not configured yet" — nothing breaks.
- The bundled runtime already includes `java.net.http` + TLS, so HTTPS calls to
  GitHub work without Java installed on the user's machine.
- Private repos would need an auth token (not implemented) — use a **public**
  repo for releases, which is the normal case for a downloadable app.
