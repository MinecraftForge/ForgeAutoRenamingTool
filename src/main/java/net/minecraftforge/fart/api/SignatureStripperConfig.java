/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.fart.api;

/**
 * Signature stripper transformation strategy for {@link Transformer#signatureStripperFactory(SignatureStripperConfig)}.
 */
public enum SignatureStripperConfig {
    /**
     * Strips all signature entries from manifests.
     */
    ALL;
    // INVALID_ONLY could be implemented if desired
}
