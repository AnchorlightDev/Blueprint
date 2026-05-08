package dev.anchorlight.blueprint.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.anchorlight.blueprint.model.AuditAction;
import dev.anchorlight.blueprint.model.SnapshotMetadata;
import dev.anchorlight.blueprint.model.WorldMetadata;
import dev.anchorlight.blueprint.model.WorldStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite implementation of {@link BlueprintStorage} backed by a HikariCP pool.
 */
public class SQLiteBlueprintStorage implements BlueprintStorage {

    private final HikariDataSource dataSource;
    private final Logger logger;

    public SQLiteBlueprintStorage(@NotNull File databaseFile, @NotNull Logger logger) {
        this.logger = logger;

        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite only supports one writer at a time
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("Blueprint-SQLite");
        // WAL mode for better concurrency
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public void initializeSchema() throws StorageException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS blueprint_worlds (
                        name        TEXT PRIMARY KEY,
                        folder_name TEXT NOT NULL,
                        owner_uuid  TEXT,
                        status      TEXT NOT NULL DEFAULT 'CLOSED',
                        created_at  INTEGER NOT NULL,
                        updated_at  INTEGER NOT NULL,
                        last_opened_at INTEGER,
                        last_closed_at INTEGER
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS blueprint_snapshots (
                        snapshot_id TEXT NOT NULL,
                        world_name  TEXT NOT NULL,
                        actor_uuid  TEXT,
                        created_at  INTEGER NOT NULL,
                        label       TEXT,
                        PRIMARY KEY (world_name, snapshot_id),
                        FOREIGN KEY (world_name) REFERENCES blueprint_worlds(name) ON DELETE CASCADE
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS blueprint_audit_log (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        action     TEXT NOT NULL,
                        world_name TEXT NOT NULL,
                        actor_uuid TEXT,
                        detail     TEXT,
                        occurred_at INTEGER NOT NULL
                    )""");

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_snapshots_world ON blueprint_snapshots(world_name)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_world ON blueprint_audit_log(world_name)");

        } catch (SQLException e) {
            throw new StorageException("Failed to initialize database schema", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ── World operations ──────────────────────────────────────────────────

    @Override
    public void saveWorld(@NotNull WorldMetadata meta) throws StorageException {
        String sql = """
                INSERT INTO blueprint_worlds
                    (name, folder_name, owner_uuid, status, created_at, updated_at, last_opened_at, last_closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET
                    folder_name    = excluded.folder_name,
                    owner_uuid     = excluded.owner_uuid,
                    status         = excluded.status,
                    updated_at     = excluded.updated_at,
                    last_opened_at = excluded.last_opened_at,
                    last_closed_at = excluded.last_closed_at
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, meta.getName());
            ps.setString(2, meta.getFolderName());
            ps.setString(3, meta.getOwnerUuid() != null ? meta.getOwnerUuid().toString() : null);
            ps.setString(4, meta.getStatus().name());
            ps.setLong(5, meta.getCreatedAt().toEpochMilli());
            ps.setLong(6, meta.getUpdatedAt().toEpochMilli());
            ps.setObject(7, meta.getLastOpenedAt() != null ? meta.getLastOpenedAt().toEpochMilli() : null);
            ps.setObject(8, meta.getLastClosedAt() != null ? meta.getLastClosedAt().toEpochMilli() : null);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save world: " + meta.getName(), e);
        }
    }

    @Override
    public @Nullable WorldMetadata getWorld(@NotNull String name) throws StorageException {
        String sql = "SELECT * FROM blueprint_worlds WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapWorld(rs);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to fetch world: " + name, e);
        }
        return null;
    }

    @Override
    public List<WorldMetadata> listWorlds(int page, int pageSize) throws StorageException {
        String sql = "SELECT * FROM blueprint_worlds ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<WorldMetadata> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, pageSize);
            ps.setInt(2, (page - 1) * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapWorld(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to list worlds", e);
        }
        return result;
    }

    @Override
    public List<WorldMetadata> listAllWorlds() throws StorageException {
        String sql = "SELECT * FROM blueprint_worlds ORDER BY created_at DESC";
        List<WorldMetadata> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) result.add(mapWorld(rs));
        } catch (SQLException e) {
            throw new StorageException("Failed to list all worlds", e);
        }
        return result;
    }

    @Override
    public void deleteWorld(@NotNull String name) throws StorageException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM blueprint_worlds WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete world: " + name, e);
        }
    }

    @Override
    public void renameWorld(@NotNull String oldName, @NotNull String newName, @NotNull String newFolderName)
            throws StorageException {
        String updateWorld     = "UPDATE blueprint_worlds SET name = ?, folder_name = ?, updated_at = ? WHERE name = ?";
        String updateSnapshots = "UPDATE blueprint_snapshots SET world_name = ? WHERE world_name = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psWorld = conn.prepareStatement(updateWorld);
                 PreparedStatement psSnaps = conn.prepareStatement(updateSnapshots)) {

                psWorld.setString(1, newName);
                psWorld.setString(2, newFolderName);
                psWorld.setLong(3, Instant.now().toEpochMilli());
                psWorld.setString(4, oldName);
                psWorld.executeUpdate();

                psSnaps.setString(1, newName);
                psSnaps.setString(2, oldName);
                psSnaps.executeUpdate();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to rename world '" + oldName + "' to '" + newName + "'", e);
        }
    }

    // ── Snapshot operations ───────────────────────────────────────────────

    @Override
    public void saveSnapshot(@NotNull SnapshotMetadata meta) throws StorageException {
        String sql = """
                INSERT OR REPLACE INTO blueprint_snapshots
                    (snapshot_id, world_name, actor_uuid, created_at, label)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, meta.getSnapshotId());
            ps.setString(2, meta.getWorldName());
            ps.setString(3, meta.getActorUuid() != null ? meta.getActorUuid().toString() : null);
            ps.setLong(4, meta.getCreatedAt().toEpochMilli());
            ps.setString(5, meta.getLabel());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save snapshot: " + meta.getSnapshotId(), e);
        }
    }

    @Override
    public List<SnapshotMetadata> listSnapshots(@NotNull String worldName) throws StorageException {
        String sql = "SELECT * FROM blueprint_snapshots WHERE world_name = ? ORDER BY created_at DESC";
        List<SnapshotMetadata> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, worldName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapSnapshot(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to list snapshots for: " + worldName, e);
        }
        return result;
    }

    @Override
    public @Nullable SnapshotMetadata getSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException {
        String sql = "SELECT * FROM blueprint_snapshots WHERE world_name = ? AND snapshot_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, worldName);
            ps.setString(2, snapshotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapSnapshot(rs);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to fetch snapshot: " + snapshotId, e);
        }
        return null;
    }

    @Override
    public void deleteSnapshot(@NotNull String worldName, @NotNull String snapshotId) throws StorageException {
        String sql = "DELETE FROM blueprint_snapshots WHERE world_name = ? AND snapshot_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, worldName);
            ps.setString(2, snapshotId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to delete snapshot: " + snapshotId, e);
        }
    }

    // ── Audit log ─────────────────────────────────────────────────────────

    @Override
    public void logAudit(
            @NotNull AuditAction action,
            @NotNull String worldName,
            @Nullable UUID actorUuid,
            @Nullable String detail) throws StorageException {

        String sql = """
                INSERT INTO blueprint_audit_log (action, world_name, actor_uuid, detail, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, action.name());
            ps.setString(2, worldName);
            ps.setString(3, actorUuid != null ? actorUuid.toString() : null);
            ps.setString(4, detail);
            ps.setLong(5, Instant.now().toEpochMilli());
            ps.executeUpdate();

        } catch (SQLException e) {
            // Audit log failure should not break the calling operation – log and continue
            logger.warning("[Blueprint] Failed to write audit log entry: " + e.getMessage());
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private WorldMetadata mapWorld(ResultSet rs) throws SQLException {
        String ownerStr = rs.getString("owner_uuid");
        Long lastOpened = (Long) rs.getObject("last_opened_at");
        Long lastClosed = (Long) rs.getObject("last_closed_at");

        return new WorldMetadata(
                rs.getString("name"),
                rs.getString("folder_name"),
                ownerStr != null ? UUID.fromString(ownerStr) : null,
                WorldStatus.valueOf(rs.getString("status")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("updated_at")),
                lastOpened != null ? Instant.ofEpochMilli(lastOpened) : null,
                lastClosed != null ? Instant.ofEpochMilli(lastClosed) : null
        );
    }

    private SnapshotMetadata mapSnapshot(ResultSet rs) throws SQLException {
        String actorStr = rs.getString("actor_uuid");
        return new SnapshotMetadata(
                rs.getString("snapshot_id"),
                rs.getString("world_name"),
                actorStr != null ? UUID.fromString(actorStr) : null,
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getString("label")
        );
    }
}
