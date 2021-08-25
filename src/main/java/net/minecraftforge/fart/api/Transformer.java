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

import java.util.Collection;
import java.util.Collections;

import net.minecraftforge.fart.internal.EntryImpl;
import net.minecraftforge.fart.internal.RenamingTransformer;
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

    public static Transformer createRenamer(Inheritance inh, IMappingFile map) {
        return new RenamingTransformer(inh, map);
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
