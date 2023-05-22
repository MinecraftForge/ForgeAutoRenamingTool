/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.internal;

import net.minecraftforge.fart.api.ClassProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

class SortedClassProvider implements ClassProvider {
    List<ClassProvider> classProviders;
    private final Consumer<String> log;
    private final Map<String, Optional<? extends IClassInfo>> classCache = new ConcurrentHashMap<>();

    SortedClassProvider(List<ClassProvider> classProviders, Consumer<String> log) {
        this.classProviders = classProviders;
        this.log = log;
    }

    @Override
    public Optional<? extends IClassInfo> getClass(String cls) {
        return this.classCache.computeIfAbsent(cls, this::computeClassInfo);
    }

    private Optional<? extends IClassInfo> computeClassInfo(String name) {
        for (ClassProvider classProvider : this.classProviders) {
            Optional<? extends IClassInfo> classInfo = classProvider.getClass(name);

            if (classInfo.isPresent())
                return classInfo;
        }

        this.log.accept("Can't Find Class: " + name);

        return Optional.empty();
    }

    void clearCache() {
        this.classCache.clear();
    }

    @Override
    public void close() throws IOException {
        for (ClassProvider classProvider : this.classProviders) {
            classProvider.close();
        }
    }
}
