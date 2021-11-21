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

package net.minecraftforge.fart.api;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import net.minecraftforge.fart.internal.FFLineFixer;
import net.minecraftforge.fart.internal.IdentifierFixer;
import net.minecraftforge.fart.internal.ParameterAnnotationFixer;
import net.minecraftforge.fart.internal.EntryImpl;
import net.minecraftforge.fart.internal.RecordFixer;
import net.minecraftforge.fart.internal.RenamingTransformer;
import net.minecraftforge.fart.internal.SourceFixer;
import net.minecraftforge.srgutils.IMappingFile;

public interface Transformer {
    default ClassEntry process(ClassEntry entry) {
        return entry;
    }
    default ManifestEntry process(ManifestEntry entry) {
        return entry;
    }
    default ResourceEntry process(ResourceEntry entry) {
        return entry;
    }
    default Collection<? extends Entry> getExtras() {
        return Collections.emptyList();
    }

    /**
     * Create a transformer that applies mappings as a transformation.
     *
     * @param inh inheritance information, including remapping classpath
     * @param map the mapping information to remap with
     * @return a renaming transformer
     */
    public static Transformer createRenamer(Inheritance inh, IMappingFile map) {
        return new RenamingTransformer(inh, map);
    }

    /**
     * Create a transformer that renames any local variables that are not valid java identifiers.
     *
     * @param config option for which local variables to rename
     * @return an identifier-fixing transformer
     */
    public static Transformer createIdentifierFixer(final IdentifierFixerConfig config) {
        return new IdentifierFixer(config);
    }

    /**
     * Create a transformer that fixes misaligned parameter annotations caused by Proguard.
     *
     * @return a parameter annotation-fixing transformer
     */
    public static Transformer createParameterAnnotationFixer() {
        return ParameterAnnotationFixer.INSTANCE;
    }

    /**
     * Create a transformer that applies line number corrections from Fernflower.
     *
     * @param sourceJar the source jar
     * @return a transformer that applies line number information
     */
    public static Transformer createFernFlowerLineFixer(File sourceJar) {
        return new FFLineFixer(sourceJar);
    }

    /**
     * Create a transformer that restores record component data stripped by ProGuard.
     *
     * @return a transformer that fixes record class metadata
     */
    public static Transformer createRecordFixer() {
        return RecordFixer.INSTANCE;
    }

    /**
     * Create a transformer that fixes the {@code SourceFile} attribute of classes.
     *
     * This attempts to infer a file name based on the supplied language information.
     *
     * @param config the method to use to generate a source file name.
     * @return a transformer that fixes {@code SourceFile} information
     */
    public static Transformer createSourceFixer(SourceFixerConfig config) {
        return new SourceFixer(config);
    }

    public interface Entry {
        static final long STABLE_TIMESTAMP = 0x386D4380; //01/01/2000 00:00:00 java 8 breaks when using 0.
        long getTime();
        String getName();
        byte[] getData();
        Entry process(Transformer transformer);
    }

    public interface ClassEntry extends Entry {
        static ClassEntry create(String name, long time, byte[] data) {
            return new EntryImpl.ClassEntry(name, time, data);
        }
        static ClassEntry create(String cls, long time, byte[] data, int version) {
            return create("META-INF/versions/" + version + '/' +  cls + ".class", time, data);
        }

        String getClassName();
        boolean isMultiRelease();
        int getVersion();
    }

    public interface ResourceEntry extends Entry {
        static ResourceEntry create(String name, long time, byte[] data) {
            return new EntryImpl.ResourceEntry(name, time, data);
        }
    }

    public interface ManifestEntry extends Entry {
        static ManifestEntry create(long time, byte[] data) {
            return new EntryImpl.ManifestEntry(time, data);
        }
    }
}
