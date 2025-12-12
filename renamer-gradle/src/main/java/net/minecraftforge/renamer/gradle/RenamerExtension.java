/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jspecify.annotations.Nullable;

/// The extension interface for the Renamer Gradle plugin.
public sealed interface RenamerExtension permits RenamerExtensionInternal {
    /// The name for this extension when added to [projects][org.gradle.api.Project].
    String NAME = "renamer";

    default void mappings(String channel, String version) {
        mappings("net.minecraft:mappings_" + channel + ':' + version + "@tsrg.gz");
    }

    void mappings(String artifact);
    void mappings(Dependency dependency);

    default void rename(AbstractArchiveTask task) {
        rename(task, (Action<RenamerTask>)null);
    }

    default void rename(AbstractArchiveTask task, @Nullable Action<RenamerTask> config) {
        rename(task, task.getName() + "Rename", config);
    }

    default void rename(AbstractArchiveTask task, String name) {
        rename(task, name, null);
    }

    void rename(AbstractArchiveTask task, String name, @Nullable Action<RenamerTask> config);

    void setTool(String coords);
    String getTool();
}
