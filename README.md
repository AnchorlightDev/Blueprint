# Blueprint

**The world manager your build server has been waiting for.**

Blueprint turns world administration on [Paper](https://papermc.io/) into a handful of clean, memorable commands. Spin up fresh build worlds, clone a finished project in seconds, snapshot your work before a risky change, and lock down what's done — all without touching the console, juggling files, or restarting the server.

Built for build teams, event servers, and creative networks that treat their worlds like the assets they are.

---

## Why Blueprint?

- ⚡ **Instant worlds** — `/bp create myworld` and you're building. Void worlds load fast and stay lag-free, with mobs, weather, and griefing already dialed out of the way.
- 📋 **One-command cloning** — duplicate any world, terrain and all, with `/bp clone`. Perfect for templates, contest copies, and "let me try something" moments.
- 📸 **Built-in snapshots** — capture a restore point before every big change. Roll back instantly when an experiment goes sideways — with an automatic safety backup taken before each restore.
- 🔒 **Lockable worlds** — flip a finished build to read-only so nobody fat-fingers your spawn.
- 🧭 **Players never get stranded** — unload a world and anyone inside is whisked safely to your fallback world.
- 🗂️ **Everything in one place** — all managed worlds live under a single `blueprint/` directory, neatly organized and out of your server root.
- 🛡️ **Safe by default** — deletions require explicit confirmation, and operations are name-validated and path-guarded.

---

## Requirements

- **Java** 21 or newer
- **Paper** 1.21 or newer

## Installation

**1. Point Paper's world container at Blueprint.** Add this to `bukkit.yml`:

```yaml
settings:
  world-container: blueprint
```

**2. Move your existing worlds in.** Place your `world/`, `world_nether/`, and `world_the_end/` folders inside a new `blueprint/` folder, then restart. Your worlds now live at `/server/blueprint/<name>/`.

**3. Drop in the plugin.** Copy `Blueprint-0.1.0.jar` into `plugins/` and restart.

That's it — you're ready to build.

---

## Commands

Use `/blueprint` or the shorthand `/bp`.

| Command | What it does |
|---------|--------------|
| `/bp create <world>` | Create a brand-new build world |
| `/bp import <world>` | Register an existing world folder |
| `/bp list [page]` | Browse your managed worlds |
| `/bp tp <world>` | Teleport to a world |
| `/bp hub` | Jump back to the hub world |
| `/bp open <world>` | Load a world |
| `/bp close <world>` | Unload a world (players evacuated safely) |
| `/bp lock <world>` | Lock a world — no edits allowed |
| `/bp unlock <world>` | Unlock a world |
| `/bp clone <source> <target>` | Duplicate a world, terrain and all |
| `/bp rename <world> <new-name>` | Rename a world |
| `/bp snapshot create <world>` | Capture a restore point |
| `/bp snapshot list <world>` | List a world's snapshots |
| `/bp snapshot restore <world> <id>` | Roll a world back to a snapshot |
| `/bp snapshot delete <world> <id>` | Remove a snapshot |
| `/bp delete <world> confirm` | Permanently delete a world |

> **Importing a world:** copy its folder into `blueprint/<name>/`, then run `/bp import <name>`.

---

## Permissions

Grant `blueprint.admin` for full access. Every command also has its own node (`blueprint.command.<name>`) for fine-grained control. All default to enabled, except `blueprint.bypass.lock`, which is op-only.

---

## Configuration

Tune Blueprint to your server in `plugins/Blueprint/config.yml`:

```yaml
worlds:
  container-directory: "blueprint"   # must match world-container in bukkit.yml
  fallback-world: "world"            # where players go when a world unloads
  hub-world: "world"                 # where /bp hub sends players
  auto-open-created-worlds: true     # load new worlds immediately
  auto-open-clones: true             # load clones immediately

snapshots:
  enabled: true
  auto-backup-before-restore: true   # safety snapshot before each restore
  max-per-world: 25                  # 0 = unlimited

safety:
  require-delete-confirmation: true  # require "confirm" on /bp delete
```

Snapshots are stored alongside each world at `blueprint/<world>/snapshots/<id>/`, so they travel with the world if you ever move it.

---

<p align="center">
  <sub>Crafted by <a href="https://github.com/anchorlightdev/blueprint">Anchorlight</a> for Paper.</sub>
</p>
