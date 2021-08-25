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

import net.minecraftforge.fart.api.Inheritance;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.Renamer.Builder;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;

public class RenamerBuilder implements Builder {
    private final Inheritance inh = Inheritance.create();
    private File input;
    private File output;
    private List<File> libraries = new ArrayList<>();
    private List<Transformer> transformers = new ArrayList<>();

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
            add(Transformer.createRenamer(inh, IMappingFile.load(value)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public Builder add(Transformer value) {
        this.transformers.add(value);
        return this;
    }

    @Override
    public Renamer build() {
        return new RenamerImpl(input, output, libraries, transformers, inh);
    }
}
