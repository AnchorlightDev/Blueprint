# Blueprint Manual Test Checklist

Use this checklist when testing Blueprint on a Paper server.
All commands require OP or `blueprint.admin` unless otherwise noted.

---

## Environment

- [ ] Paper 1.21.x (primary target)
- [ ] Paper latest (best-effort check)
- [ ] Java 21+
- [ ] At least one default world (`world`) loaded as fallback

---

## 1. Plugin Load

- [ ] Plugin enables without errors in console
- [ ] `plugins/Blueprint/config.yml` is generated
- [ ] `plugins/Blueprint/blueprint.db` is created
- [ ] `plugins/Blueprint/snapshots/` directory exists
- [ ] `/blueprint help` shows all commands

---

## 2. Create World

```
/blueprint create testworld
```
- [ ] Success message shown
- [ ] `blueprint_testworld/` folder created in server root
- [ ] `/blueprint list` shows `testworld` with status `OPEN` (if auto-open enabled)
- [ ] Try duplicate: `/blueprint create testworld` → error "already exists"
- [ ] Try invalid name: `/blueprint create TEST World!` → error "invalid name"
- [ ] Try short name: `/blueprint create ab` → error "invalid name"

---

## 3. List Worlds

```
/blueprint list
/blueprint list 2
```
- [ ] First page shows worlds
- [ ] Page 2 of empty → "No worlds found on page 2"
- [ ] Status colors: green=OPEN, gray=CLOSED, red=LOCKED

---

## 4. Teleport

```
/blueprint tp testworld
```
- [ ] Player teleported to spawn of `blueprint_testworld`
- [ ] Closed world: `/blueprint tp testworld` → error "is closed"
- [ ] Non-existent: `/blueprint tp doesnotexist` → error "not found"
- [ ] Console: `/blueprint tp testworld` → error "players only"

---

## 5. Close / Open

```
/blueprint close testworld
```
- [ ] Players in world are teleported to fallback world
- [ ] World is unloaded (not shown in `/list` as a Bukkit world)
- [ ] Status shows `CLOSED`

```
/blueprint open testworld
```
- [ ] World loads
- [ ] Status shows `OPEN`
- [ ] Closing an already-closed world → error "already closed"
- [ ] Opening an already-open world → error "already open"

---

## 6. Lock / Unlock

```
/blueprint lock testworld
```
- [ ] Status shows `LOCKED`
- [ ] Player without bypass: break a block → denied with message
- [ ] Player without bypass: place a block → denied
- [ ] Player without bypass: attack entity → denied
- [ ] Player with `blueprint.bypass.lock`: can break/place normally

```
/blueprint unlock testworld
```
- [ ] Status returns to `OPEN`
- [ ] Blocks can be placed/broken again

---

## 7. Clone World

```
/blueprint clone testworld testclone
```
- [ ] Success message after async operation
- [ ] `blueprint_testclone/` exists on disk
- [ ] `blueprint_testclone/uid.dat` does NOT exist
- [ ] `blueprint_testclone/session.lock` does NOT exist
- [ ] `/blueprint list` shows `testclone` (OPEN if auto-open-clones=true)
- [ ] Original world (`testworld`) is still accessible
- [ ] Try cloning non-existent source → error "not found"
- [ ] Try cloning to existing target → error "already exists"
- [ ] Try invalid target name → error "invalid name"

---

## 8. Snapshot Create

```
/blueprint snapshot create testworld
```
- [ ] Success message with snapshot ID (e.g. `snap-2026-05-07-153022`)
- [ ] Snapshot folder created: `plugins/Blueprint/snapshots/testworld/snap-2026-05-07-153022/`
- [ ] World is re-opened after snapshot if it was open
- [ ] Snapshots disabled: set `snapshots.enabled: false`, restart, try → error "disabled"

---

## 9. Snapshot List

```
/blueprint snapshot list testworld
```
- [ ] Lists all snapshots newest-first with IDs and timestamps
- [ ] No snapshots → "No snapshots for world..."
- [ ] Non-existent world → error "not found"

---

## 10. Snapshot Restore

```
/blueprint snapshot restore testworld snap-<id>
```
- [ ] Pre-restore backup is created (check `plugins/Blueprint/snapshots/testworld/`)
- [ ] World folder replaced with snapshot contents
- [ ] `uid.dat` removed from restored folder
- [ ] World is re-loaded if it was open before restore
- [ ] Non-existent snapshot ID → error "not found"

---

## 11. Snapshot Delete

```
/blueprint snapshot delete testworld snap-<id>
```
- [ ] Snapshot folder deleted from disk
- [ ] Snapshot removed from list
- [ ] Non-existent ID → error "not found"

---

## 12. Delete World

```
/blueprint delete testworld
```
- [ ] With `require-delete-confirmation: true` → prompts to add "confirm"

```
/blueprint delete testworld confirm
```
- [ ] Success message
- [ ] World unloaded
- [ ] `blueprint_testworld/` folder deleted from disk
- [ ] `/blueprint list` no longer shows `testworld`
- [ ] Concurrent delete blocked: try running two deletes rapidly → second should fail with "busy"

---

## 13. Restart Persistence

1. Create a world: `/blueprint create persist`
2. Stop server
3. Start server
4. Run `/blueprint list` → `persist` still listed
5. Run `/blueprint open persist` → world loads correctly
6. Create a snapshot, restart, run `/blueprint snapshot list persist` → snapshot still listed

---

## 14. Operation Lock Conflicts

1. Start a clone: `/blueprint clone bigworld clone1`
2. Immediately try to delete source: `/blueprint delete bigworld confirm`
3. → Second operation should fail with "is currently busy"

---

## 15. Permission Tests

With a non-OP player who has no Blueprint permissions:
- [ ] All commands return "no permission" message

With a player who has `blueprint.bypass.lock`:
- [ ] Can modify blocks in a LOCKED world

---

## 16. Edge Cases

- [ ] Teleport to a world that exists in DB but folder is missing → appropriate error in console
- [ ] Create world name at min length (3 chars): `/blueprint create abc` → OK
- [ ] Create world name at max length (32 chars): `/blueprint create aaaabbbbccccddddeeeeffffgggghhhh` → OK
- [ ] Create world name 33 chars → error invalid name
- [ ] `/blueprint snapshot restore testworld snap-id` when world is busy → error "busy"
