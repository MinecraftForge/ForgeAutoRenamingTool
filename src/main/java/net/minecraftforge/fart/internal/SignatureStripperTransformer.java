/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.internal;

import net.minecraftforge.fart.api.SignatureStripperConfig;
import net.minecraftforge.fart.api.Transformer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class SignatureStripperTransformer implements Transformer {
    private final Consumer<String> log;
    private final SignatureStripperConfig config;

    public SignatureStripperTransformer(Consumer<String> log, SignatureStripperConfig config) {
        this.log = log;
        this.config = config;
    }

    @Override
    public ManifestEntry process(ManifestEntry entry) {
        // Remove all signature entries
        // see signed jar spec: https://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html#Signed_JAR_File
        try {
            final Manifest manifest = new Manifest(new ByteArrayInputStream(entry.getData()));
            boolean found = false;
            for (final Iterator<Map.Entry<String, Attributes>> it = manifest.getEntries().entrySet().iterator(); it.hasNext();) {
                final Map.Entry<String, Attributes> section = it.next();
                for (final Iterator<Map.Entry<Object, Object>> attrIter = section.getValue().entrySet().iterator(); attrIter.hasNext();) {
                    final Map.Entry<Object, Object> attribute = attrIter.next();
                    final String key = attribute.getKey().toString().toLowerCase(Locale.ROOT); // spec says this is case-insensitive
                    if (key.endsWith("-digest")) { // assume that this is a signature entry
                        if (this.config == SignatureStripperConfig.ALL) {
                            attrIter.remove();
                        }
                        found = true;
                    }
                    // keep going even if we've found an attribute -- multiple hash formats can be specified for each file
                }

                if (section.getValue().isEmpty()) {
                    it.remove();
                }
            }
            if (found) {
                try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                    manifest.write(os);
                    return ManifestEntry.create(entry.getTime(), os.toByteArray());
                }
            }
        } catch (final IOException ex) {
            log.accept("Failed to remove signature entries from manifest: " + ex);
        }
        return entry;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        // Signature metadata
        if (entry.getName().startsWith("META-INF/")) {
            if (entry.getName().endsWith(".RSA")
                    || entry.getName().endsWith(".SF")
                    || entry.getName().endsWith(".DSA")
                    || entry.getName().endsWith(".EC")) { // supported by InstallerRewriter but not referenced in the spec
                return null;
            }
        }
        return entry;
    }
}
