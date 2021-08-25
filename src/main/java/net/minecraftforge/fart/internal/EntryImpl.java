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

import net.minecraftforge.fart.api.Transformer;

public abstract class EntryImpl implements Transformer.Entry {
    private final String name;
    private final long time;
    private final byte[] data;

    protected EntryImpl(String name, long time, byte[] data) {
        this.name = name;
        this.time = time;
        this.data = data;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public long getTime() {
        return this.time;
    }

    @Override
    public byte[] getData() {
        return this.data;
    }

    public static class ClassEntry extends EntryImpl implements Transformer.ClassEntry {
        private static final String VERSION_PREFIX = "META-INF/versions/";
        private final int release;
        private final String className;

        public ClassEntry(String name, long time, byte[] data) {
            super(name, time, data);
            if (name.startsWith(VERSION_PREFIX)) {
                int start = VERSION_PREFIX.length();
                int idx = name.indexOf('/', start);
                if (idx == -1)
                    throw new IllegalArgumentException("Invalid versioned class entry: " + name);
                release = Integer.parseInt(name.substring(start, idx));
                name = name.substring(start + idx + 1);
            } else {
                release = -1;
            }
            className = name.substring(0, name.length() - 6);
        }

        @Override
        public Transformer.ClassEntry process(Transformer transformer) {
            return transformer.process(this);
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public boolean isMultiRelease() {
            return release != -1;
        }

        @Override
        public int getVersion() {
            return release;
        }
    }

    public static class ResourceEntry extends EntryImpl implements Transformer.ResourceEntry {
        public ResourceEntry(String name, long time, byte[] data) {
            super(name, time, data);
        }

        @Override
        public Transformer.ResourceEntry process(Transformer transformer) {
            return transformer.process(this);
        }
    }

    public static class ManifestEntry extends EntryImpl implements Transformer.ManifestEntry {
        public ManifestEntry(long time, byte[] data) {
            super("META-INF/MANIFEST.MF", time, data);
        }

        @Override
        public Transformer.ManifestEntry process(Transformer transformer) {
            return transformer.process(this);
        }
    }
}
