package dev.anchorlight.blueprint.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

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
        copyDirectory(source, target, Collections.emptyList());
    }

    /**
     * Recursively copies {@code source} into {@code target}, excluding specific top-level entries.
     *
     * @param excludeNames names of files/folders in the source root to skip
     * @throws IOException on any IO failure
     */
    public static void copyDirectory(@NotNull Path source, @NotNull Path target, @NotNull List<String> excludeNames) throws IOException {
        Path normSource = source.toAbsolutePath().normalize();
        Path normTarget = target.toAbsolutePath().normalize();

        if (!Files.isDirectory(normSource)) {
            String details = !Files.exists(normSource) ? " (does not exist)" :
                             !Files.isReadable(normSource) ? " (not readable)" :
                             Files.isRegularFile(normSource) ? " (is a regular file)" : " (not a directory)";
            throw new IllegalArgumentException("Source is not a directory: " + normSource + details);
        }
        if (Files.exists(normTarget) && !Files.isDirectory(normTarget)) {
            throw new IllegalArgumentException("Target already exists and is not a directory: " + normTarget);
        }

        Files.walkFileTree(normSource, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (dir.equals(normSource)) {
                            Files.createDirectories(normTarget);
                            return FileVisitResult.CONTINUE;
                        }

                        Path relative = normSource.relativize(dir);
                        if (relative.getNameCount() == 1 && excludeNames.contains(relative.getFileName().toString())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        Path dest = normTarget.resolve(relative);
                        Files.createDirectories(dest);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relative = normSource.relativize(file);
                        if (relative.getNameCount() == 1 && excludeNames.contains(relative.getFileName().toString())) {
                            return FileVisitResult.CONTINUE;
                        }

                        Path dest = normTarget.resolve(relative);
                        Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
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
        deleteDirectory(path, Collections.emptyList());
    }

    /**
     * Recursively deletes a directory, excluding specific top-level entries.
     *
     * @param excludeNames names of files/folders in the root to skip
     * @throws IOException on any IO failure
     */
    public static void deleteDirectory(@NotNull Path path, @NotNull List<String> excludeNames) throws IOException {
        Path normPath = path.toAbsolutePath().normalize();
        if (!Files.exists(normPath)) return;

        Files.walkFileTree(normPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(normPath)) return FileVisitResult.CONTINUE;

                Path relative = normPath.relativize(dir);
                if (relative.getNameCount() == 1 && excludeNames.contains(relative.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = normPath.relativize(file);
                if (relative.getNameCount() == 1 && excludeNames.contains(relative.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                if (dir.equals(normPath)) {
                    if (excludeNames.isEmpty()) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
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
