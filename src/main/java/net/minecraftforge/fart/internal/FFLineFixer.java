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
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import net.minecraftforge.fart.api.Transformer;

public final class FFLineFixer implements Transformer {
    private final Map<String, NavigableMap<Integer, Integer>> classes = new HashMap<>();

    public FFLineFixer(Consumer<String> debug, File data) {
        try (FileInputStream fis = new FileInputStream(data);
            ZipInputStream zip = new ZipInputStream(fis)) {
            ZipEntry entry = null;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] extra = entry.getExtra();
                if (extra == null || !entry.getName().endsWith(".java"))
                    continue;

                ByteBuffer buf = ByteBuffer.wrap(extra);
                buf.order(ByteOrder.LITTLE_ENDIAN);

                while (buf.hasRemaining()) {
                    short id  = buf.getShort();
                    short len = buf.getShort();
                    if (id == 0x4646) { //FF
                        String cls = entry.getName().substring(0, entry.getName().length() - 5);
                        debug.accept("Lines: " + cls);
                        int ver = buf.get();
                        if (ver != 1)
                            throw new IllegalStateException("Invalid FF code line version for " + entry.getName());
                        int count = (len - 1) / 4;
                        NavigableMap<Integer, Integer> lines = new TreeMap<>();
                        for (int x = 0; x < count; x++) {
                            int oline = buf.getShort();
                            int nline = buf.getShort();
                            debug.accept("  " + oline + ' ' + nline);
                            lines.put(oline, nline);
                        }
                        classes.put(cls, lines);
                    } else {
                        buf.position(buf.position() + len);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not create FFLineFixer for file: " + data.getAbsolutePath(), e);
        }
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String owner = entry.getClassName();
        int idx = owner.indexOf('$');
        if (idx != -1)
            owner = owner.substring(0, idx);

        NavigableMap<Integer, Integer> lines = classes.get(owner);
        if (lines == null)
            return entry;

        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(reader, 0);
        Fixer fixer = new Fixer(writer, lines);

        reader.accept(fixer, 0);

        if (!fixer.madeChange())
            return entry;

        return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
    }

    private static class Fixer extends ClassVisitor {
        private final NavigableMap<Integer, Integer> lines;
        private boolean madeChange = false;

        public Fixer(ClassVisitor parent, NavigableMap<Integer, Integer> lines) {
            super(RenamerImpl.MAX_ASM_VERSION, parent);
            this.lines = lines;
        }

        public boolean madeChange() {
            return this.madeChange;
        }

        @Override
        public final MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
            MethodVisitor parent = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(RenamerImpl.MAX_ASM_VERSION, parent) {
                @Override
                public void visitLineNumber(final int line, final Label start) {
                    Map.Entry<Integer, Integer> nline = lines.higherEntry(line);
                    if (nline != null) {
                        madeChange = true;
                        super.visitLineNumber(nline.getValue(), start);
                    } else {
                        super.visitLineNumber(line, start);
                    }
                }
            };
        }
    }
}
