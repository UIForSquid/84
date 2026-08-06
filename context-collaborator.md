# MINDMAP//84 - Collaborator Guide (Markdown Exchange)

You've been sent this because we're collaborating through MINDMAP//84's markdown
export/import. This document tells you everything you need to read, edit, and
create those `.md` files correctly.

**You do not need the app installed to contribute.** These are plain text files.
You can edit them in VS Code, Notepad, Obsidian, or anything else. If you do have
the app, you'll use the **Export MD** and **Import MD** buttons in each tool.

---

## The three file types

The app has three tools, each with its own markdown format:

| Tool | What it is | File shape |
|---|---|---|
| **Mindmap** | A nested tree of nodes, each with a color and freeform notes | Node markers + headings |
| **Checklist** | Grouped checkboxes | `- [ ]` task lists |
| **Datalist** | Grouped numbered lists with descriptions | `1. Item: description` |

**Every file begins with a type marker on the first line.** It's an HTML comment,
so it's invisible when the markdown is rendered, but the app requires it:

```
<!--84:mindmap v1-->
<!--84:checklist v1-->
<!--84:datalist v1-->
```

If that line is missing or altered, the app rejects the file with
"Could not read that file as a mind map." Never delete it.

---

## The golden rule: the parser is strict

The importer is **all-or-nothing**. If it hits a single line it doesn't
recognize, it rejects the **entire file** and the existing data is left
untouched. Nothing is partially imported.

This is deliberate (it protects against importing garbage), but it means:

> **Do not add prose, comments, headers, or blank-line "decoration" to these files
> outside the places the format allows.** No title block at the top, no "notes for
> reviewer" paragraph at the bottom.

If you want to leave a message for me, put it **inside a node's notes block**
(mindmap) or send it separately. Blank lines are always fine and ignored.

---

## Format 1: Mindmap

The tree structure lives in HTML comments; the text between them is yours.

```markdown
<!--84:mindmap v1-->

<!--84:node d0 #6600ff @120,-338-->
# Ouroboros SMP
<!--84:notes-->
# Overview
This is the root of the whole map.

- notes can contain bullets
- **bold**, headings, numbered lists
- all of it is preserved exactly as typed
<!--84:/notes-->

<!--84:node d1 #ff9500 @-45,902-->
## Classes

<!--84:node d2 #ff2d95 @310,640-->
### Fighting Styles

<!--84:node d3 #ff2d95 @520,700-->
#### Spearman
```

### How it works

Each node is **two lines** (plus an optional notes block):

1. **The marker line:** `<!--84:node dN #rrggbb @x,y-->`
   - `dN` is the **depth**: `d0` = a root node, `d1` = its child, `d2` = grandchild, and so on. Unlimited depth.
   - `#rrggbb` is the node's color - exactly six hex digits, with the `#`.
   - `@x,y` is the node's **position on the canvas** - whole numbers, either sign,
     with no space after the `@`. See "Positions" below.
2. **The label line:** a markdown heading. The text after the `#` characters is the node's name.
   - The number of `#` is cosmetic only (it just makes the file readable as an outline). **Depth comes from `dN`, not from the heading level.** Headings stop at `######` but depth doesn't.

### Nesting rules

A node at depth `dN` attaches to **the most recent node at depth `dN-1`** above it -
exactly like an indented outline. So order matters: write nodes top-to-bottom in
the order they should nest.

**You cannot skip a depth level.** Going straight from `d1` to `d3` rejects the
file, because there's no `d2` parent to attach to.

### Positions

`@x,y` records where the node sits on the canvas. Larger `x` is further right,
larger `y` is further **down**; both can be negative, and there's no origin or
boundary - the canvas is infinite. Units are roughly pixels at 100% zoom, and
sibling nodes are typically ~90-250 apart.

**The `@x,y` part is optional.** If you leave it off a node, the app places that
node automatically near its parent on import. That's the easy way to add a node
by hand: write the marker without a position and let the app position it.

```markdown
<!--84:node d2 #ffb627-->
### New node I added - let the app place this one
```

Positions are preserved exactly on a round trip (rounded to whole numbers), so
if the layout has been arranged deliberately, **don't reshuffle the `@x,y`
values** - editing text and structure won't disturb the layout.

### Notes blocks

Optional. If a node has notes, they go immediately after the label line:

```markdown
<!--84:notes-->
whatever you want, verbatim
<!--84:/notes-->
```

Everything between the markers is preserved **byte for byte** - your headings,
bold, bullets, indentation, and blank lines all survive a round trip unchanged.
That's why the structure uses comments: so a `# Heading` inside your notes is
never mistaken for a new node.

A node with no notes simply omits the block. Don't leave an empty one.

### Formatting that will break the import

These are the things most likely to bite you:

- **Don't indent the marker or heading lines.** They must start at column 0.
- **The closing `<!--84:/notes-->` must be alone on its line, at column 0, with no trailing spaces.**
- **Don't put any other text between nodes.** Outside a notes block, the only things allowed are marker lines, heading lines, and blank lines.
- **Colors must be full 6-digit hex** - `#f00` is invalid, use `#ff0000`.
- **Don't leave a file with only the header line** - a mindmap with zero nodes is rejected.

(Edge case you'll probably never hit: if a line *inside* your notes needs to
literally start with `<!--84`, prefix it with a backslash: `\<!--84...`. The app
adds and strips this automatically.)

---

## Format 2: Checklist

Standard GitHub-style task lists. `## ` lines are groups.

```markdown
<!--84:checklist v1-->

## Fruit
- [x] Apples
- [ ] Bananas
- [ ] Cherries

## Vegetables
- [ ] Carrots
```

- `- [x]` = checked, `- [ ]` = unchecked (capital `- [X]` also works).
- Items before any `## ` group land in a group called "Ungrouped".
- Anything that isn't a `## ` line, a `- [ ]` item, or a blank line rejects the file.

---

## Format 3: Datalist

Numbered items, with an optional description after a colon.

```markdown
<!--84:datalist v1-->

## Bosses
1. Meta Knight: dodge the tornado
2. King Dedede
3. Kracko: stay out of the corners
```

- The **numbers are ignored on import** - they're renumbered from 1 when the app
  exports again. So you can insert an item without renumbering everything.
- Text before the first `:` is the item; everything after is its description.
- **Gotcha:** an item whose name legitimately contains a colon will split at that
  colon. `Boss: Round 2: hard mode` becomes item `Boss` with description
  `Round 2: hard mode`. Avoid colons in item names.
- Items before any `## ` group land in "Ungrouped".

---

## Working with me: the exchange loop

1. I click **Export MD** and send you the `.md` file.
   (A mindmap exports named after its root node, e.g. `Ouroboros SMP.md`.)
2. You edit it, or write a new one from scratch using the formats above.
3. You send it back.
4. I click **Import MD**, confirm the replace prompt, and your version is live.

### Two things to know about importing

- **Import replaces everything, it does not merge.** Importing a mindmap wipes the
  current map and installs yours. There is no automatic merge and no undo, so
  we should not both edit the same file at the same time - one of us will lose
  work. Coordinate who "has the file."
- **Node positions round-trip.** The canvas layout is carried in the `@x,y` on
  each node marker, so exporting and re-importing leaves the map looking the
  same. Only nodes written without an `@x,y` get auto-placed.

### Practical tips

- Save as **UTF-8** with a `.md` extension.
- The files render fine in any markdown viewer (GitHub, VS Code preview,
  Obsidian) - the `<!--84...-->` markers are invisible comments, so a mindmap
  reads as a clean nested outline. Good for reviewing before sending back.
- These files are plain text, so they diff and version-control well. If you want
  a safety net, keep a copy of the original before you edit.
- If an import is rejected, the most common causes in order are: a missing or
  edited first-line marker, an indented heading, a skipped depth level, and
  stray prose between nodes.

---

## Quick templates

Copy-paste starting points.

**New mindmap** (no `@x,y` anywhere, so the app lays it all out for you):
```markdown
<!--84:mindmap v1-->

<!--84:node d0 #00f0ff-->
# Project Name
<!--84:notes-->
What this project is.
<!--84:/notes-->

<!--84:node d1 #ff2d95-->
## First Area

<!--84:node d2 #ffb627-->
### A detail under that area
```

**New checklist:**
```markdown
<!--84:checklist v1-->

## Group Name
- [ ] First task
- [ ] Second task
```

**New datalist:**
```markdown
<!--84:datalist v1-->

## Group Name
1. First item: what it means
2. Second item
```

Handy colors from the app's palette: `#00f0ff` cyan, `#ff2d95` magenta,
`#b537f2` purple, `#ffb627` amber, `#39ff14` lime, `#ff6b6b` red,
`#4d9fff` blue. Any valid 6-digit hex works.
