/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.internal;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import net.minecraftforge.fart.api.ClassProvider;

public class ClassProviderBuilderImpl implements ClassProvider.Builder {
    private final List<FileSystem> fileSystems = new ArrayList<>();
    private final Map<String, Path> sources = new HashMap<>();
    private final Map<String, Optional<? extends ClassProvider.IClassInfo>> classInfos = new ConcurrentHashMap<>();
    private boolean cacheAll = false;

    public ClassProviderBuilderImpl() {}

    @Override
    public ClassProvider.Builder addLibrary(Path path) {
        try {
            Path libraryDir;
            if (Files.isDirectory(path)) {
                libraryDir = path;
            } else if (Files.isRegularFile(path)) {
                FileSystem zipFs = FileSystems.newFileSystem(path, (ClassLoader) null);
                this.fileSystems.add(zipFs);
                libraryDir = zipFs.getPath("/");
            } else {
                // We can't load it (it doesn't exist)
                return this;
            }

            try (Stream<Path> walker = Files.walk(libraryDir)) {
                walker.forEach(fullPath -> {
                    Path relativePath = libraryDir.relativize(fullPath);
                    String pathName = relativePath.toString().replace('\\', '/');
                    if (!pathName.endsWith(".class") || pathName.startsWith("META-INF"))
                        return;
                    this.sources.putIfAbsent(pathName.substring(0, pathName.length() - 6), fullPath);
                });
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not add library: " + path.toAbsolutePath(), e);
        }

        return this;
    }

    @Override
    public ClassProvider.Builder addClass(String name, byte[] value) {
        this.classInfos.computeIfAbsent(name, k -> Optional.of(new ClassProviderImpl.ClassInfo(value)));

        return this;
    }

    @Override
    public ClassProvider.Builder shouldCacheAll(boolean value) {
        this.cacheAll = value;

        return this;
    }

    @Override
    public ClassProvider build() {
        return new ClassProviderImpl(this.fileSystems, this.sources, this.classInfos, this.cacheAll);
    }
}
