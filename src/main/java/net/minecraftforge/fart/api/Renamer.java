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

import java.io.Closeable;
import java.io.File;
import java.util.function.Consumer;

import net.minecraftforge.fart.internal.RenamerBuilder;

/**
 * A {@code Renamer} is used to run generic transformers on a JAR file.
 */
public interface Renamer extends Closeable {
    void run();

    /**
     * Creates a default instance of a {@link Builder}.
     * <p>
     * Logs will be logged to {@link System#out} by default.
     * Debug logs will be swallowed by default.
     * Library files can be both ZIP files and directories.
     * The default number of threads is determined from {@link Runtime#availableProcessors()}.
     */
    static Builder builder() {
        return new RenamerBuilder();
    }

    /**
     * A {@code Renamer.Builder} is used to configure and construct
     * a {@link Renamer}.
     */
    public interface Builder {
        /**
         * Sets the input JAR file to process.
         *
         * @param value the input JAR file
         * @return this builder
         */
        Builder input(File value);

        /**
         * Sets the output JAR file to write to.
         *
         * @param value the output JAR file
         * @return this builder
         */
        Builder output(File value);

        /**
         * Adds a library file to the classpath to use for inheritance.
         *
         * @param value the library file
         * @return this builder
         */
        Builder lib(File value);

        /**
         * Adds a mapping transformer using the provided mapping file.
         *
         * @param value the mapping file
         * @return this builder
         */
        Builder map(File value);

        /**
         * Adds a generic transformer to run over the input JAR file.
         *
         * @param value the transformer
         * @return this builder
         */
        Builder add(Transformer value);

        /**
         * Adds a generic transformer factory to create a transformer
         * which is then run over the input JAR file.
         *
         * @param factory the transformer factory
         * @return this builder
         */
        Builder add(Transformer.Factory factory);

        /**
         * Sets the number of asynchronous threads to use to process all entries.
         *
         * @param value the number of threads to use
         * @return this builder
         */
        Builder threads(int value);

        /**
         * Sets the logging consumer to use for standard logging.
         *
         * @param out the logging consumer
         * @return this builder
         */
        Builder logger(Consumer<String> out);

        /**
         * Sets the debug logging consumer to use for standard debug output.
         *
         * @param debug the debug logging consumer
         * @return this builder
         */
        Builder debug(Consumer<String> debug);

        /**
         * Builds the {@link Renamer} instance based on this configured builder.
         *
         * @return the built {@link Renamer}
         */
        Renamer build();
    }
}
