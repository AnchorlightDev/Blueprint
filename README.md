# Blueprint

A world management plugin for [Paper](https://papermc.io/) build servers. Create, import, clone, snapshot, lock, rename, and delete build worlds with simple commands.

## Requirements

- Java 21+
- Paper 1.21+

## Setup

Blueprint keeps all worlds in a `blueprint/` folder. Add this to `bukkit.yml` before first use:

```yaml
settings:
  world-container: blueprint
```

Then move your existing `world/`, `world_nether/`, and `world_the_end/` folders into a new `blueprint/` folder and restart. Worlds now live at `/server/blueprint/<name>/`.

Finally, drop `Blueprint-0.1.0.jar` into `plugins/` and restart.

## Commands

All commands work with `/bp` too.

| Command | Description |
|---------|-------------|
| `/bp create <world>` | Create a new world |
| `/bp import <world>` | Import an existing world |
| `/bp list [page]` | List worlds |
| `/bp tp <world>` | Teleport to a world |
| `/bp hub` | Return to the hub world |
| `/bp open <world>` | Load a world |
| `/bp close <world>` | Unload a world |
| `/bp lock <world>` | Lock a world (no edits) |
| `/bp unlock <world>` | Unlock a world |
| `/bp clone <source> <target>` | Clone a world |
| `/bp rename <world> <new-name>` | Rename a world |
| `/bp snapshot create <world>` | Snapshot a world |
| `/bp snapshot list <world>` | List snapshots |
| `/bp snapshot restore <world> <id>` | Restore a snapshot |
| `/bp snapshot delete <world> <id>` | Delete a snapshot |
| `/bp delete <world> confirm` | Delete a world |

**Importing:** copy a world folder into `blueprint/<name>/`, then run `/bp import <name>`.

## Permissions

Give `blueprint.admin` for full access. Each command also has its own node (`blueprint.command.<name>`); all default to enabled except `blueprint.bypass.lock` (op only).

## Configuration

Edit `plugins/Blueprint/config.yml`:

```yaml
worlds:
  container-directory: "blueprint"   # must match world-container in bukkit.yml
  fallback-world: "world"            # where players go when a world unloads
  hub-world: "world"                 # where /bp hub sends players
  auto-open-created-worlds: true
  auto-open-clones: true

snapshots:
  enabled: true
  auto-backup-before-restore: true   # safety snapshot before each restore
  max-per-world: 25                  # 0 = unlimited

safety:
  require-delete-confirmation: true
```

Snapshots are stored inside each world at `blueprint/<world>/snapshots/<id>/`.
