# Blueprint Manual Test Checklist

Use this on a Paper server with `settings.world-container: blueprint` set in `bukkit.yml`.
All commands require OP or `blueprint.admin` unless noted.

---

## Setup Check

- [ ] `bukkit.yml` has `settings: world-container: blueprint`
- [ ] `blueprint/` folder exists in server root (contains `world/`, `world_nether/`, `world_the_end/`)
- [ ] Plugin enables without errors
- [ ] `plugins/Blueprint/config.yml` generated
- [ ] `plugins/Blueprint/audit.yml` created after first command
- [ ] `/bp help` shows all commands

---

## 1. Create World

```
/bp create testworld
```
- [ ] Success message
- [ ] `blueprint/testworld/` folder created on disk
- [ ] `blueprint/testworld/blueprint.yml` metadata file created
- [ ] `/bp list` shows `testworld` as `OPEN` (with `auto-open-created-worlds: true`)
- [ ] Spawn platform (3x3 glass) exists at 0,64,0
- [ ] World is permanent day, no mob spawning

Invalid cases:
- [ ] `/bp create testworld` again → "already exists"
- [ ] `/bp create Test World!` → "invalid name"
- [ ] `/bp create ab` → "invalid name" (min 3 chars)

---

## 2. List

```
/bp list
/bp list 2
```
- [ ] Shows worlds with status colors (green=OPEN, gray=CLOSED, red=LOCKED)
- [ ] Empty page → "No worlds found on page 2"

---

## 3. Teleport

```
/bp tp testworld
```
- [ ] Player teleports to spawn of `blueprint/testworld/`
- [ ] `/bp tp closedworld` → "is closed"
- [ ] `/bp tp doesnotexist` → "not found"

---

## 4. Hub

```
/bp hub
```
- [ ] Player teleports to hub world (configured as `hub-world` in config)

---

## 5. Close / Open

```
/bp close testworld
```
- [ ] Players in the world are moved to fallback world with message
- [ ] World unloads from Bukkit
- [ ] `/bp list` shows `CLOSED`
- [ ] `/bp close testworld` again → "already closed"

```
/bp open testworld
```
- [ ] World loads
- [ ] `/bp list` shows `OPEN`
- [ ] `/bp open testworld` again → "already open"

---

## 6. Lock / Unlock

```
/bp lock testworld
```
- [ ] Status shows `LOCKED`
- [ ] Player without `blueprint.bypass.lock`: break a block → denied with message
- [ ] Player without `blueprint.bypass.lock`: place a block → denied
- [ ] Player with `blueprint.bypass.lock`: can edit freely

```
/bp unlock testworld
```
- [ ] Status returns to `OPEN`
- [ ] Normal block editing works again

---

## 7. Import

1. Create a folder `blueprint/importtest/` containing a valid world (copy `blueprint/testworld/` minus the `blueprint.yml`)
2. Run `/bp import importtest`
- [ ] Success message, world loads
- [ ] `blueprint/importtest/` shows as `OPEN` in `/bp list`
- [ ] World data loads correctly (not a blank void world)

Invalid cases:
- [ ] `/bp import nonexistent` → "No world data found ... blueprint/nonexistent"
- [ ] `/bp import testworld` (already registered) → "already registered"

---

## 8. Clone

```
/bp clone testworld testclone
```
- [ ] Success message after async copy
- [ ] `blueprint/testclone/` exists on disk with region data
- [ ] `blueprint/testclone/uid.dat` does NOT exist
- [ ] `blueprint/testclone/session.lock` does NOT exist
- [ ] `testclone` appears in `/bp list` (OPEN if `auto-open-clones: true`)
- [ ] Original `testworld` is still accessible
- [ ] `/bp clone missing target` → "source not found"
- [ ] `/bp clone testworld testworld` (target exists) → "already exists"

---

## 9. Rename

```
/bp rename testclone renamedworld
```
- [ ] Success message
- [ ] `blueprint/testclone/` renamed to `blueprint/renamedworld/` on disk
- [ ] `/bp list` shows `renamedworld`, not `testclone`
- [ ] `/bp tp renamedworld` works
- [ ] Old name `testclone` no longer in list
- [ ] `/bp rename renamedworld testworld` (name taken) → "already exists"

---

## 10. Snapshot Create

```
/bp snapshot create testworld
```
- [ ] Success with snapshot ID like `snap-2026-05-07-153022`
- [ ] Snapshot folder: `blueprint/testworld/snapshots/snap-2026-05-07-153022/` exists
- [ ] Snapshot folder contains region data (not empty)
- [ ] World is still open / re-opened after snapshot
- [ ] With `snapshots.enabled: false` → error "snapshots are disabled"

---

## 11. Snapshot List

```
/bp snapshot list testworld
```
- [ ] Lists snapshots newest-first with IDs and timestamps
- [ ] No snapshots → "No snapshots found"
- [ ] Unknown world → "not found"

---

## 12. Snapshot Restore

Make a change to `testworld`, create a snapshot, make another change, then:

```
/bp snapshot restore testworld snap-<id>
```
- [ ] Pre-restore backup created: `blueprint/testworld/snapshots/pre-restore-.../` exists
- [ ] World folder replaced with snapshot data
- [ ] `uid.dat` removed from restored folder
- [ ] World reloads with the restored data
- [ ] Invalid snapshot ID → "not found"

---

## 13. Snapshot Delete

```
/bp snapshot delete testworld snap-<id>
```
- [ ] Snapshot folder removed from disk
- [ ] No longer appears in `/bp snapshot list testworld`
- [ ] Invalid ID → "not found"

---

## 14. Delete World

```
/bp delete renamedworld
```
- [ ] With `require-delete-confirmation: true` → prompts to add "confirm"

```
/bp delete renamedworld confirm
```
- [ ] World unloaded, players moved to fallback
- [ ] `blueprint/renamedworld/` deleted from disk
- [ ] No longer in `/bp list`

---

## 15. Restart Persistence

1. `/bp create persist` — note it as OPEN
2. Stop server, start server
3. `/bp list` → `persist` still listed and restored as OPEN (if `close-on-restart: false`)
4. `/bp tp persist` → world loads and teleport works
5. Create a snapshot, restart, `/bp snapshot list persist` → snapshot still listed

---

## 16. Operation Lock (Concurrency)

1. Start a long clone on a large world
2. Immediately try `/bp delete <sourceName> confirm`
3. → Should fail with "is currently busy"

---

## 17. Permissions

Non-OP player with no Blueprint nodes:
- [ ] All `/bp` commands return "no permission"

Player with only `blueprint.bypass.lock`:
- [ ] Can edit blocks in a LOCKED world
- [ ] Cannot run other `/bp` commands
