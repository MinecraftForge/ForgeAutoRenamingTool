/*
 * Forge Auto Renaming Tool
 * Copyright (c) 2021
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fart.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import net.minecraftforge.fart.api.Inheritance;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;

public class RenamingTransformer implements Transformer {
    private static final Attributes.Name SHA_256_DIGEST = new Attributes.Name("SHA-256-Digest");
    private static final String ABSTRACT_FILE = "fernflower_abstract_parameter_names.txt";
    private final EnhancedRemapper remapper;
    private final Set<String> abstractParams = ConcurrentHashMap.newKeySet();
    private final Consumer<String> log;

    public RenamingTransformer(Inheritance inh, IMappingFile map, Consumer<String> log) {
        this.remapper = new EnhancedRemapper(inh, map, log);
        this.log = log;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassRemapper remapper = new EnhancedClassRemapper(writer, this.remapper, this);

        reader.accept(remapper, 0);

        byte[] data = writer.toByteArray();
        String newName = this.remapper.map(entry.getClassName());

        if (entry.isMultiRelease())
            return ClassEntry.create(newName, entry.getTime(), data, entry.getVersion());
        return ClassEntry.create(newName + ".class", entry.getTime(), data);
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
                        attrIter.remove();
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
        if (ABSTRACT_FILE.equals(entry.getName()))
            return null;

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

    @Override
    public Collection<? extends Entry> getExtras() {
        if (abstractParams.isEmpty())
            return Collections.emptyList();
        byte[] data = abstractParams.stream().sorted().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
        return Arrays.asList(ResourceEntry.create(ABSTRACT_FILE, Entry.STABLE_TIMESTAMP, data));
    }

    void storeNames(String className, String methodName, String methodDescriptor, Collection<String> paramNames) {
        abstractParams.add(className + ' ' + methodName + ' ' + methodDescriptor + ' ' + paramNames.stream().collect(Collectors.joining(" ")));
    }
}
