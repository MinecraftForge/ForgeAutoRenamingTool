/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import java.util.List;

final class Constants {
    private Constants() { }

    static final String ARTIFACT = "net.minecraftforge:ForgeAutoRenamingTool:1.1.2:all";
    static final int JAVA_VERSION = 8;

    static final List<String> DEFAULT_ARGS = List.of(
        "--input", "{input}",
        "--output", "{output}",
        "--map", "{map}",
        "--lib={library}"
    );
}
