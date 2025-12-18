/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jspecify.annotations.Nullable;

import java.io.File;

import javax.inject.Inject;

abstract class RenamerExtensionImpl implements RenamerExtensionInternal {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = Logging.getLogger(RenamerExtension.class);
    private final Project project;
    private final RenamerProblems problems = this.getObjects().newInstance(RenamerProblems.class);

    private Configuration toolConfig;
    private String tool;
    private Configuration mapConfig;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();
    protected abstract @Inject JavaToolchainService getJavaToolchains();

    @Inject
    public RenamerExtensionImpl(RenamerPlugin plugin, Project project) {
        this.project = project;
        this.setTool(Constants.ARTIFACT);
    }

    @Override
    public void setTool(String artifact) {
        this.tool = artifact;
        var dep = this.project.getDependencies().create(artifact);
        this.toolConfig = this.project.getConfigurations().detachedConfiguration(dep);
        this.toolConfig.setTransitive(false);
    }

    @Override
    public String getTool() {
        return this.tool;
    }

    @Override
    public void mappings(String artifact) {
        mappings(this.project.getDependencies().create(artifact));
    }

    @Override
    public void mappings(Dependency dependency) {
        this.mapConfig = this.project.getConfigurations().detachedConfiguration(dependency);
        this.mapConfig.setTransitive(false);
    }

    @Override
    public void rename(AbstractArchiveTask source, String name, @Nullable Action<RenamerTask> config) {
        var existing = project.getTasks().findByName(name);
        if (existing != null) {
            problems.reportIllegalTaskName(existing, name);
            return;
        }

        project.getTasks().register(name, RenamerTask.class).configure(task -> {
            // We need the compile class path so we can calculate inheritance properly.
            var javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            SourceSet mainSourceSet = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            task.getClasspath().setFrom(mainSourceSet.getCompileClasspath());

            // Set the default output file to the same as the input task, but with an extra "-renamed" classifier
            task.getOutput().convention(this.project.getLayout().file(this.getProviders().provider(() -> {
                var sourceFile = source.getArchiveFile().get().getAsFile();
                var sourceName = sourceFile.getName();
                var ext = source.getArchiveExtension().getOrNull();
                String fileName;
                if (ext == null || ext.isEmpty())
                    fileName = sourceName + "-renamed";
                else
                    fileName = sourceName.substring(0, sourceName.length() - 1 - ext.length()) + "-renamed." + ext;

                //LOGGER.lifecycle("Output Provider: " + new File(sourceFile.getParentFile(), fileName));
                return new File(sourceFile.getParentFile(), fileName);
            })));

            task.getInput().convention(source.getArchiveFile());
            if (this.mapConfig != null) // Explicitly allow null, in case people want to configure the map in the closure
                task.getMap().setFrom(this.mapConfig);
            task.getToolClasspath().setFrom(this.toolConfig);
            task.getLogFile().convention(task.getDefaultLogFile());
            task.getWorkingDir().convention(this.project.getLayout().getBuildDirectory());
            task.getArgs().convention(Constants.DEFAULT_ARGS);
            task.getJavaLauncher().convention(getJavaToolchains().launcherFor(javaExtension.getToolchain()));

            if (config != null)
                config.execute(task);
        });
    }
}
