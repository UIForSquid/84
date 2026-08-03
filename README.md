# MINDMAP//84

A neon, 80s-cyberpunk **mind map + checklist + datalist** desktop app for Windows,
with a built-in **GitHub auto-updater**.

- **Mind map** — link-scaled nodes, drag, zoom/pan, ambient wobble, per-node notes
  (bottom-centre panel + full "wiki" page), subtree delete, a faint synthwave city
  skyline behind the canvas.
- **Checklist** — grouped checkboxes, progress bar, hide-done / reset / collapse.
- **Datalist** — numbered ordered items with descriptions and up/down reordering.

Your saved data lives in `%USERPROFILE%\.mindmap84\` and is never touched by
installing or updating.

## Download & run

1. Grab **`MINDMAP84-windows.zip`** from the latest [Release](../../releases/latest).
2. Unzip it anywhere (keep the folder together).
3. Run **`MINDMAP84.exe`** — no install, no Java needed (a trimmed Java runtime is
   bundled). Or run **`Installer.exe`** to install it to a folder + make a desktop
   shortcut.

## Updating

Click **⟳ Check for Updates** (top-right in the app). It pulls the newest release
from this repo, downloads it, and restarts on the new version.

## Building from source

Requires a JDK with `jpackage` (JDK 17+; built with JDK 25).

```powershell
cd desktop
./build.ps1        # or: bash build.sh
```

Produces `desktop/dist/MINDMAP84/` (with `MINDMAP84.exe` + `Installer.exe`).
Zip that folder into `MINDMAP84-windows.zip` for a release.

See [`desktop/packaging/RELEASING.md`](desktop/packaging/RELEASING.md) for the full
release + auto-update setup.

## Repo layout

- `desktop/` — the Java/Swing desktop app (the current product).
- `MINDMAP-84/` — the original web (HTML/JS/CSS) version, kept for reference.
