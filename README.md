# Blueprint

Blueprint is a modern build-server world management plugin for [Paper](https://papermc.io/), inspired by Scaffold but rewritten from scratch for Paper 1.21+.

It provides clean, admin-friendly commands for the full lifecycle of build worlds: creation, cloning, snapshots, rollback, locking, and safe deletion — all with async file I/O and SQLite persistence.

---

## Requirements

| Component | Version |
|-----------|---------|
| Java | 21+ |
| Paper | 1.21.x – latest |
| Server RAM | 512 MB minimum (more recommended for large worlds) |

Blueprint does **not** use NMS, CraftBukkit internals, or reflection-based version hacks.

---

## Installation

1. Download `Blueprint-0.1.0.jar` from the releases page.
2. Place it in your server's `plugins/` folder.
3. Start the server. Blueprint will generate `plugins/Blueprint/config.yml`.
4. Edit `config.yml` as needed, then restart.

---

## Commands

All commands support the alias `/bp`.

| Command | Description |
|---------|-------------|
| `/blueprint help` | Show all available commands |
| `/blueprint version` | Show plugin version info |
| `/blueprint create <world>` | Create a new build world |
| `/blueprint list [page]` | List all managed worlds |
| `/blueprint tp <world>` | Teleport to a world's spawn |
| `/blueprint open <world>` | Load and open a world |
| `/blueprint close <world>` | Unload and close a world |
| `/blueprint lock <world>` | Lock a world against modifications |
| `/blueprint unlock <world>` | Unlock a previously locked world |
| `/blueprint clone <source> <target>` | Clone a world into a new world |
| `/blueprint snapshot create <world>` | Create a snapshot of a world |
| `/blueprint snapshot list <world>` | List snapshots for a world |
| `/blueprint snapshot restore <world> <id>` | Restore a snapshot |
| `/blueprint snapshot delete <world> <id>` | Delete a snapshot |
| `/blueprint delete <world> confirm` | Permanently delete a world |

---

## Permissions

| Node | Description | Default |
|------|-------------|---------|
| `blueprint.admin` | Grants all Blueprint permissions | OP |
| `blueprint.command.help` | View help | OP |
| `blueprint.command.version` | View version | OP |
| `blueprint.command.create` | Create worlds | OP |
| `blueprint.command.list` | List worlds | OP |
| `blueprint.command.teleport` | Teleport to worlds | OP |
| `blueprint.command.open` | Open worlds | OP |
| `blueprint.command.close` | Close worlds | OP |
| `blueprint.command.lock` | Lock worlds | OP |
| `blueprint.command.unlock` | Unlock worlds | OP |
| `blueprint.command.clone` | Clone worlds | OP |
| `blueprint.command.snapshot` | Manage snapshots | OP |
| `blueprint.command.delete` | Delete worlds | OP |
| `blueprint.bypass.lock` | Bypass locked-world protection | OP |

Assign `blueprint.admin` to grant all permissions at once.

---

## Configuration

`plugins/Blueprint/config.yml`:

```yaml
storage:
  type: sqlite          # Only sqlite is currently supported

worlds:
  folder-prefix: "blueprint_"       # Prefix added to Bukkit world folder names
  page-size: 10                     # Worlds per /blueprint list page
  fallback-world: "world"           # Players are sent here when a world unloads
  auto-open-created-worlds: true    # Load the world immediately after creation
  auto-open-clones: true            # Load clone targets automatically

snapshots:
  enabled: true
  auto-backup-before-restore: true  # Creates a safety snapshot before every restore
  max-per-world: 25                 # Oldest snapshots are pruned after this limit

safety:
  require-delete-confirmation: true # /blueprint delete <world> confirm required
  allowed-world-name-regex: "^[a-z0-9_-]{3,32}$"

messages:
  prefix: "<gradient:#00aaff:#0066ff><bold>Blueprint</bold></gradient> <dark_gray>»</dark_gray> "
```

---

## Snapshot Layout

```
plugins/Blueprint/
  snapshots/
    <worldName>/
      snap-2026-05-07-153022/   <- snapshot folder (full world copy)
      pre-restore-2026-05-07-160000/  <- auto-backup before restore
```

Snapshot IDs follow the pattern `snap-YYYY-MM-DD-HHmmss` (UTC).

---

## Version Compatibility

| Paper version | Status |
|---------------|--------|
| 1.21.x | Fully supported |
| 1.22+ | Best-effort (no NMS, should work) |

Blueprint targets the Paper API (`api-version: 1.21`) and avoids internal APIs, so it should run on future Paper versions without modification.

---

## Safety Notes

- **Snapshots** are plain directory copies. They are not atomic. Avoid stopping the server mid-snapshot.
- **Always close a world** before cloning or restoring. Blueprint does this automatically, but a server crash mid-operation could leave the folder in a partial state.
- **`auto-backup-before-restore: true`** is strongly recommended in production. This creates a named pre-restore snapshot so you can recover from accidental restores.
- **`max-per-world`** caps snapshot storage per world. Set to `0` for unlimited (and manage storage yourself).
- Blueprint never touches the default `world`, `world_nether`, or `world_the_end` folders. All managed worlds use the configured `folder-prefix`.
- The `uid.dat` and `session.lock` files are removed from cloned/restored worlds to prevent world UID conflicts.
