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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraftforge.fart.api.ClassProvider;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.Renamer.Builder;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;

import static java.util.Objects.requireNonNull;

public class RenamerBuilder implements Builder {
    private File input;
    private File output;
    private final List<File> libraries = new ArrayList<>();
    private final List<ClassProvider> classProviders = new ArrayList<>();
    private final List<Transformer.Factory> transformerFactories = new ArrayList<>();
    private int threads = Runtime.getRuntime().availableProcessors();
    private boolean withJvmClasspath = false;
    private Consumer<String> logger = System.out::println;
    private Consumer<String> debug = s -> {};

    @Override
    public Builder input(File value) {
        this.input = value;
        return this;
    }

    @Override
    public Builder output(File value) {
        this.output = value;
        return this;
    }

    @Override
    public Builder lib(File value) {
        this.libraries.add(value);
        return this;
    }

    @Override
    public Builder map(File value) {
        try {
            add(Transformer.renamerFactory(IMappingFile.load(value)));
        } catch (IOException e) {
            throw new RuntimeException("Could not map file: " + value.getAbsolutePath(), e);
        }
        return this;
    }

    @Override
    public Builder addClassProvider(ClassProvider classProvider) {
        this.classProviders.add(classProvider);
        return this;
    }

    @Override
    public Builder withJvmClasspath() {
        this.withJvmClasspath = true;
        return this;
    }

    @Override
    public Builder add(Transformer value) {
        this.transformerFactories.add(Transformer.Factory.always(requireNonNull(value, "value")));
        return this;
    }

    @Override
    public Builder add(Transformer.Factory factory) {
        this.transformerFactories.add(requireNonNull(factory, "factory"));
        return this;
    }

    @Override
    public Builder threads(int value) {
        this.threads = value;
        return this;
    }

    @Override
    public Builder logger(Consumer<String> out) {
        this.logger = requireNonNull(out, "out");
        return this;
    }

    @Override
    public Builder debug(Consumer<String> debug) {
        this.debug = requireNonNull(debug, "debug");
        return this;
    }

    @Override
    public Renamer build() {
        if (this.withJvmClasspath)
            this.classProviders.add(ClassProvider.fromJvmClasspath());

        SortedClassProvider sortedClassProvider = new SortedClassProvider(this.classProviders, this.logger);
        final Transformer.Context ctx = new Transformer.Context() {
            @Override
            public Consumer<String> getLog() {
                return logger;
            }

            @Override
            public Consumer<String> getDebug() {
                return debug;
            }

            @Override
            public ClassProvider getClassProvider() {
                return sortedClassProvider;
            }
        };

        final List<Transformer> transformers = new ArrayList<>(transformerFactories.size());
        for (Transformer.Factory factory : transformerFactories) {
            transformers.add(requireNonNull(factory.create(ctx), "output of " + factory));
        }
        return new RenamerImpl(input, output, libraries, transformers, sortedClassProvider, threads, logger, debug);
    }
}
