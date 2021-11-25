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
import java.util.function.Consumer;

import net.minecraftforge.fart.internal.FFLineFixer;
import net.minecraftforge.fart.internal.IdentifierFixer;
import net.minecraftforge.fart.internal.ParameterAnnotationFixer;
import net.minecraftforge.fart.internal.EntryImpl;
import net.minecraftforge.fart.internal.RecordFixer;
import net.minecraftforge.fart.internal.RenamingTransformer;
import net.minecraftforge.fart.internal.SignatureStripperTransformer;
import net.minecraftforge.fart.internal.SourceFixer;
import net.minecraftforge.srgutils.IMappingFile;

import static java.util.Objects.requireNonNull;

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
     * @deprecated use {@link #renamerFactory(IMappingFile)} instead
     */
    @Deprecated
    public static Transformer createRenamer(Inheritance inh, IMappingFile map) {
        return new RenamingTransformer(inh, map, System.out::println);
    }

    /**
     * Create a transformer that applies mappings as a transformation.
     *
     * @param map the mapping information to remap with
     * @return a factory for a renaming transformer
     */
    public static Factory renamerFactory(IMappingFile map) {
        return ctx -> new RenamingTransformer(ctx.getInheritance(), map, ctx.getLog());
    }

    /**
     * Create a transformer that renames any local variables that are not valid java identifiers.
     *
     * @param config option for which local variables to rename
     * @return an identifier-fixing transformer
     */
    public static Factory identifierFixerFactory(final IdentifierFixerConfig config) {
        return ctx -> new IdentifierFixer(config);
    }

    /**
     * Create a transformer that fixes misaligned parameter annotations caused by Proguard.
     *
     * @return a factory for a parameter annotation-fixing transformer
     */
    public static Factory parameterAnnotationFixerFactory() {
        return ctx -> new ParameterAnnotationFixer(ctx.getLog(), ctx.getDebug());
    }

    /**
     * Create a transformer that applies line number corrections from Fernflower.
     *
     * @param sourceJar the source jar
     * @return a factory for a transformer that applies line number information
     */
    public static Factory fernFlowerLineFixerFactory(File sourceJar) {
        return ctx -> new FFLineFixer(ctx.getDebug(), sourceJar);
    }

    /**
     * Create a transformer that restores record component data stripped by ProGuard.
     *
     * @return a factory for a transformer that fixes record class metadata
     */
    public static Factory recordFixerFactory() {
        return ctx -> RecordFixer.INSTANCE;
    }

    /**
     * Create a transformer that fixes the {@code SourceFile} attribute of classes.
     *
     * This attempts to infer a file name based on the supplied language information.
     *
     * @param config the method to use to generate a source file name.
     * @return a transformer that fixes {@code SourceFile} information
     */
    public static Factory sourceFixerFactory(SourceFixerConfig config) {
        return ctx -> new SourceFixer(config);
    }

    /**
     * Create a transformer that strips invalid code signing signatures from a manifest.
     *
     * @param config the variants of signatures to strip
     * @return a factory for a transformer that strips signatures
     */
    public static Factory signatureStripperFactory(SignatureStripperConfig config) {
        return ctx -> new SignatureStripperTransformer(ctx.getLog(), config);
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

    /**
     * A factory to create transformers using {@link Renamer} instance-specific information.
     */
    public interface Factory {
        /**
         * Create a new factory that always returns the same transformer instance.
         *
         * @param transformer the transformer
         * @return a new transformer factory
         */
        public static Factory always(final Transformer transformer) {
            requireNonNull(transformer, "transformer");
            return ctx -> transformer;
        }

        /**
         * Create a new transformer.
         *
         * @param ctx context
         * @return a transformer instance
         */
        Transformer create(Context ctx);
    }

    /**
     * Context providing renamer state when creating a transformer.
     */
    public interface Context {
        /**
         * Get a consumer that will handle standard-level logging output.
         *
         * @return the logging handler
         */
        Consumer<String> getLog();

        /**
         * Get a consumer that will handle debug-level logging output.
         *
         * @return the debug logging handler
         */
        Consumer<String> getDebug();
        Inheritance getInheritance();
    }
}
