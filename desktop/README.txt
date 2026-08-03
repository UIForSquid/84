MINDMAP//84 — Desktop Edition
=============================

A native Windows desktop version of the MINDMAP//84 app (mind map +
checklist + datalist), written in Java (Swing) and packaged with a bundled
Java runtime. No install, no browser, no internet, nothing else to set up.


HOW TO RUN
----------
1. Open the  MINDMAP84  folder (the one containing MINDMAP84.exe).
2. Double-click  MINDMAP84.exe.

That's it. The Java runtime it needs is bundled inside the folder, so you do
NOT need Java installed. Keep the folder together — the .exe needs the
"runtime" and "app" folders next to it. (Move/copy the whole MINDMAP84 folder,
not just the .exe.)


INSTALLING / UPDATING
---------------------
- The app is portable: you can just run MINDMAP84.exe from the folder.
- To install it properly, run  Installer.exe  (in the same folder). It copies
  the app to a location you pick and makes a desktop shortcut.
- To update, click  ⟳ Check for Updates  in the top-right of the app. It pulls
  new versions from GitHub and restarts on the new one. (This starts working
  once the GitHub repo is set in Config.java — see packaging/RELEASING.md.)


WHERE IT SAVES
--------------
Your data is written to:   C:\Users\<you>\.mindmap84\
  - mindmap.json     the mind map
  - checklist.json   the checklists
  - datalist.json    the ordered lists

Saving there (not inside the app folder) means your work survives even if you
move, replace, or re-download the MINDMAP84 folder. Everything autosaves as you
edit, and each screen also has a Save/Load button.


USING IT
--------
Top bar switches between the three tools: Mindmap · Checklist · Datalist.

MIND MAP
  - "+ New Root Node"   makes a top-level circle.
  - Click a node        selects it; the right-hand inspector opens.
  - Drag a node         moves it.
  - Scroll wheel        zooms (centered on the cursor).
  - Drag empty space    pans the board.
  - "+ Add Child"       adds a node under the selected one (you STAY on the
                        parent, so you can keep adding more).
  - Label / Color       rename / recolor the selected node.
  - Notes               shown read-only in a panel at the bottom-centre of the
                        screen; click its "Edit" button (top-right) to edit,
                        then "Done" to save.
  - "Center"            pans the view to the selected node.
  - "Delete" / Del key  removes a node AND its whole subtree.
  - Left sidebar        the hierarchy; click an entry to open that node's full
                        WIKI PAGE (title, editable notes, and links to its
                        parent/children). "Back to Map" returns to the canvas.
  - Zoom                scroll wheel; zooms straight in/out on screen centre.
  - Node size           grows gently with how many descendants it has.
  - Nodes gently drift  (a subtle ambient wobble); saved positions never move.

CHECKLIST
  - Paste text, then Generate (replace) or + Add (merge).
  - A line is a group header if it starts/ends with  =  _  |  ~
    e.g.  === Fruit ===   ~~~ Veg ~~~   | DC |
  - Items are comma-separated. Tick them off; the bar tracks progress.
  - Hide Done / Expand / Collapse / Reset / Save / Load.

DATALIST
  - Like the checklist but ordered + numbered, with optional descriptions
    after a colon, e.g.   Meta Knight : dodge the tornado
  - Reorder items with the up/down arrows.


REBUILDING FROM SOURCE (optional)
---------------------------------
You only need this if you want to change the code. Requires a JDK 17+ with
jpackage (this was built with Temurin JDK 25).

  Windows PowerShell:   .\build.ps1
  Git Bash:             ./build.sh

The build compiles src/mindmap84/*.java, makes a jar, and runs jpackage to
regenerate  dist/MINDMAP84/  (the folder above).

Quick logic check without building the app:
  javac -d out src/mindmap84/*.java
  java -cp out mindmap84.Main --selftest
