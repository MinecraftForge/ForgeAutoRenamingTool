/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import javax.inject.Inject;

import org.gradle.api.Task;
import org.gradle.api.problems.Severity;

import java.io.Serial;

abstract class RenamerProblems extends EnhancedProblems {
    private static final @Serial long serialVersionUID = -5334414678185075096L;

    @Inject
    public RenamerProblems() {
        super(RenamerExtension.NAME, RenamerPlugin.DISPLAY_NAME);
    }

    void reportIllegalTaskName(Task existing, String name) {
        this.getLogger().error("ERROR: Cannot register renamer task {}, name already exists", name);
        this.report("rename-duplicate-task-name", "Cannot register renamer task", spec -> spec
            .details("""
                Cannot register renamer task, as a task with that name already exists.
                Name: %s"""
                .formatted(name))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Use the `renamer.rename` methods that take in a explicit task name.")
            .solution(HELP_MESSAGE));
    }

    void reportNoMainClass(RenamerTask task) {
        this.getLogger().error("ERROR: Failed to find Main-Class for Renamer Tool");
        this.report("rename-no-main-class", "Renamer tool not executable jar", spec -> spec
            .details("""
                When using a custom renamer tool, it must be a executable jar. With no transitive depdencies.
                If this is not the case, then you must specify the main class using %s.mainClass = 'some.class'
            """.formatted(task.getName()))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Specify main class for " + task.getName())
            .solution(HELP_MESSAGE));
    }

    void reportMultipleMapFiles(RenamerTask task) {
        this.getLogger().error("ERROR: Failed to find Mapping File");
        this.report("rename-multiple-map-files", "Renamer Map File returned to many files", spec -> spec
            .details("""
                Only expected one file for the renaming map task. If using a configuration to resolve the file
                be sure to disable transtive dependencies.
            """.formatted(task.getName()))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Specify one map file for " + task.getName())
            .solution(HELP_MESSAGE));
    }
}
