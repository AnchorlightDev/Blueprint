# Blueprint

Blueprint is a modern build-server world management plugin for [Paper](https://papermc.io/), written for Paper 1.21+.

It provides clean, admin-friendly commands for the full lifecycle of build worlds: creation, importing, cloning, snapshots, rollback, locking, renaming, and safe deletion — all with async file I/O and YAML persistence.

---

## Requirements

| Component | Version |
|-----------|---------|
| Java | 21+ |
| Paper | 1.21.x – latest |

Blueprint does **not** use NMS, CraftBukkit internals, or reflection-based version hacks.

---

## Installation

### 1. Configure bukkit.yml

Blueprint requires Paper to use a dedicated world sub-folder. Add this to your server's `bukkit.yml` **before** starting the server with Blueprint for the first time:

```yaml
settings:
  world-container: blueprint
```

This tells Paper to store all worlds inside `blueprint/` relative to the server root. Blueprint worlds then live at predictable, flat paths:

```
/server/
  blueprint/
    world/          ← your main world (move it here once, see below)
    world_nether/
    world_the_end/
    myworld/        ← a Blueprint world
    another/        ← another Blueprint world
```

> **One-time migration:** Stop the server, move your existing `world/`, `world_nether/`, and `world_the_end/` folders into the new `blueprint/` directory, add the `world-container` setting, then start the server.

### 2. Install the plugin

1. Download `Blueprint-0.1.0.jar` from the releases page.
2. Place it in `plugins/`.
3. Start the server. Blueprint generates `plugins/Blueprint/config.yml`.
4. Edit `config.yml` as needed, then restart.

---

## World Storage

With `world-container: blueprint` set, every world on the server — including your main worlds — lives under `blueprint/`. Blueprint-managed worlds are stored at:

```
blueprint/<name>/
```

For example, a world named `myworld` is stored at:

```
/server/blueprint/myworld/
```

No prefixes, no sub-namespacing, no hidden dimension folders.

---

## Commands

All commands support the alias `/bp`.

| Command | Description |
|---------|-------------|
| `/blueprint help [page]` | Show all available commands |
| `/blueprint version` | Show plugin version info |
| `/blueprint create <world>` | Create a new build world |
| `/blueprint import <world>` | Import an existing world into Blueprint |
| `/blueprint list [page]` | List all managed worlds |
| `/blueprint tp <world>` | Teleport to a world's spawn |
| `/blueprint hub` | Return to the hub world |
| `/blueprint open <world>` | Load and open a world |
| `/blueprint close <world>` | Unload and close a world |
| `/blueprint lock <world>` | Lock a world against modifications |
| `/blueprint unlock <world>` | Unlock a previously locked world |
| `/blueprint clone <source> <target>` | Clone a world into a new world |
| `/blueprint rename <world> <new-name>` | Rename a world |
| `/blueprint snapshot create <world>` | Create a snapshot of a world |
| `/blueprint snapshot list <world>` | List snapshots for a world |
| `/blueprint snapshot restore <world> <id>` | Restore a snapshot |
| `/blueprint snapshot delete <world> <id>` | Delete a snapshot |
| `/blueprint delete <world> confirm` | Permanently delete a world |

### Importing an existing world

To bring an existing world folder into Blueprint:

1. Copy the world folder into `blueprint/` and name it after the world you want, e.g. `blueprint/myworld/` (it should contain `region/`, `entities/`, `poi/`, etc.).
2. Run `/bp import myworld`.

Blueprint validates that region data exists before registering the world.

---

## Permissions

All permissions default to `true` except `blueprint.bypass.lock` which defaults to `op`. Assign `blueprint.admin` to grant everything at once.

| Node | Description |
|------|-------------|
| `blueprint.admin` | Grants all Blueprint permissions |
| `blueprint.command.help` | View help |
| `blueprint.command.version` | View version |
| `blueprint.command.create` | Create worlds |
| `blueprint.command.import` | Import existing worlds |
| `blueprint.command.list` | List worlds |
| `blueprint.command.teleport` | Teleport to worlds |
| `blueprint.command.hub` | Use /blueprint hub |
| `blueprint.command.open` | Open worlds |
| `blueprint.command.close` | Close worlds |
| `blueprint.command.lock` | Lock worlds |
| `blueprint.command.unlock` | Unlock worlds |
| `blueprint.command.clone` | Clone worlds |
| `blueprint.command.snapshot` | Manage snapshots |
| `blueprint.command.rename` | Rename worlds |
| `blueprint.command.delete` | Delete worlds |
| `blueprint.bypass.lock` | Bypass locked-world protection (default: op) |

---

## Configuration

`plugins/Blueprint/config.yml`:

```yaml
storage:
  # Supported types: "yaml"
  type: yaml

worlds:
  # Must match settings.world-container in bukkit.yml
  container-directory: "blueprint"
  # Number of worlds shown per /blueprint list page
  page-size: 10
  # World to send players to when a world is unloaded/closed
  fallback-world: "world"
  # World players are sent to with /blueprint hub (defaults to fallback-world)
  hub-world: "world"
  # Automatically load (open) worlds immediately after creation
  auto-open-created-worlds: true
  # Automatically load (open) clone targets after cloning completes
  auto-open-clones: true
  # If true, worlds that were OPEN or LOCKED before a restart are marked CLOSED
  # on startup instead of being automatically reloaded
  close-on-restart: false

snapshots:
  enabled: true
  # Create an automatic backup snapshot before restoring
  auto-backup-before-restore: true
  # Maximum snapshots stored per world (oldest is pruned first, 0 = unlimited)
  max-per-world: 25

safety:
  # Require the "confirm" keyword for /blueprint delete
  require-delete-confirmation: true
  # Regex that world names must match
  allowed-world-name-regex: "^[a-z0-9_-]{3,32}$"

messages:
  prefix: "<gradient:#00aaff:#0066ff><bold>Blueprint</bold></gradient> <dark_gray>»</dark_gray> "
```

---

## Snapshot Layout

Snapshots are stored inside the world folder:

```
blueprint/<worldName>/
  region/
  entities/
  ...
  snapshots/
    snap-2026-05-07-153022/         ← snapshot (full world copy, excluding snapshots/)
    pre-restore-2026-05-07-160000/  ← auto-backup created before a restore
```

Snapshot IDs follow the pattern `snap-YYYY-MM-DD-HHmmss` (UTC). Pre-restore backups use `pre-restore-YYYY-MM-DD-HHmmss`.

---

## Safety Notes

- **Snapshots** are plain directory copies — they are not atomic. Avoid stopping the server mid-snapshot.
- **Always close a world** before cloning or restoring. Blueprint does this automatically, but a crash mid-operation could leave the folder in a partial state.
- **`auto-backup-before-restore: true`** is strongly recommended in production. It creates a named pre-restore snapshot so you can recover from accidental restores.
- **`max-per-world: 0`** disables the snapshot limit. Manage storage yourself if you use this.
- Blueprint deletes and renames world folders only within the configured `world-container` directory. It will never touch folders outside that container.
- The `uid.dat` and `session.lock` files are removed from cloned and restored worlds to prevent world UID conflicts.
