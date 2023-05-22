/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.api;

/**
 * Source file naming strategy for {@link Transformer#sourceFixerFactory(SourceFixerConfig)}.
 */
public enum SourceFixerConfig {
    // Uses java style Source file names, this means inner classes get the parent, and it uses a .java extension.
    JAVA;
    // If people care they can PR scala/kotlin/groovy, or map based support
}
