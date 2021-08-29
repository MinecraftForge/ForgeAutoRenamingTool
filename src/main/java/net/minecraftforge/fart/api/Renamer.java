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

import net.minecraftforge.fart.internal.RenamerBuilder;

public interface Renamer {
    void run();

    static Builder builder() {
        return new RenamerBuilder();
    }

    public interface Builder {
        Builder input(File value);
        Builder output(File value);
        Builder lib(File value);
        Builder map(File value);
        Builder add(Transformer value);
        Builder threads(int value);
        Renamer build();
    }
}
