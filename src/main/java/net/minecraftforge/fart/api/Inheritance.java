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
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraftforge.fart.internal.InheritanceImpl;

import static java.util.Objects.requireNonNull;

public interface Inheritance {
    static Inheritance create() {
        return new InheritanceImpl(System.out::println);
    }

    static Inheritance create(Consumer<String> out) {
        return new InheritanceImpl(requireNonNull(out));
    }

    void addLibrary(File path);
    void addClass(String cls, byte[] data);
    Optional<? extends IClassInfo> getClass(String cls);

    public interface IClassInfo {
        int getAccess();
        String getName();
        String getSuper();
        Collection<String> getInterfaces();
        Collection<? extends IFieldInfo> getFields();
        Optional<? extends IFieldInfo> getField(String name); //TODO: Desc?
        Collection<? extends IMethodInfo> getMethods();
        Optional<? extends IMethodInfo> getMethod(String name, String desc);
    }

    public interface IFieldInfo {
        int getAccess();
        String getName();
        String getDescriptor();
    }

    public interface IMethodInfo {
        int getAccess();
        String getName();
        String getDescriptor();
    }
}
