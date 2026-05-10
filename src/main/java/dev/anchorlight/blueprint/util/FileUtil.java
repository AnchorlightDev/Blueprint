package dev.anchorlight.blueprint.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

/**
 * Safe file-system utilities for Blueprint operations.
 * All methods use {@link java.nio.file} APIs and validate paths before acting.
 */
public final class FileUtil {

    private FileUtil() {}

    /**
     * Recursively copies {@code source} into {@code target}.
     * {@code target} must not already exist.
     *
     * @throws IllegalArgumentException if either path is null or target already exists
     * @throws IOException              on any IO failure
     */
    public static void copyDirectory(@NotNull Path source, @NotNull Path target) throws IOException {
        Path normSource = source.toAbsolutePath().normalize();
        Path normTarget = target.toAbsolutePath().normalize();

        if (!Files.isDirectory(normSource)) {
            String details = !Files.exists(normSource) ? " (does not exist)" :
                             !Files.isReadable(normSource) ? " (not readable)" :
                             Files.isRegularFile(normSource) ? " (is a regular file)" : " (not a directory)";
            throw new IllegalArgumentException("Source is not a directory: " + normSource + details);
        }
        if (Files.exists(normTarget)) {
            throw new IllegalArgumentException("Target already exists: " + normTarget);
        }

        Files.walkFileTree(normSource, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path dest = normTarget.resolve(normSource.relativize(dir));
                        Files.createDirectories(dest);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path dest = normTarget.resolve(normSource.relativize(file));
                        Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /**
     * Recursively deletes a directory and all its contents.
     * Silently does nothing if {@code path} does not exist.
     *
     * @throws IOException on any IO failure
     */
    public static void deleteDirectory(@NotNull Path path) throws IOException {
        Path normPath = path.toAbsolutePath().normalize();
        if (!Files.exists(normPath)) return;

        Files.walkFileTree(normPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Computes the total size in bytes of all files under {@code path}.
     *
     * @return 0 if the path does not exist
     * @throws IOException on any IO failure
     */
    public static long directorySize(@NotNull Path path) throws IOException {
        Path normPath = path.toAbsolutePath().normalize();
        if (!Files.exists(normPath)) return 0L;
        long[] size = {0L};
        Files.walkFileTree(normPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return size[0];
    }

    /**
     * Ensures that {@code child} is inside {@code base}, preventing path-traversal attacks.
     *
     * @throws SecurityException if child escapes base
     */
    public static void ensureInsideDirectory(@NotNull Path base, @NotNull Path child) {
        Path normalBase  = base.toAbsolutePath().normalize();
        Path normalChild = child.toAbsolutePath().normalize();
        if (!normalChild.startsWith(normalBase)) {
            throw new SecurityException("Path escape detected: " + normalChild + " is not inside " + normalBase);
        }
    }

    /**
     * Deletes a single file if it exists; silently ignores missing files.
     */
    public static void deleteIfExists(@NotNull Path path) throws IOException {
        Files.deleteIfExists(path.toAbsolutePath().normalize());
    }
}
