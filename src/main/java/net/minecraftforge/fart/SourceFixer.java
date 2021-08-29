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

package net.minecraftforge.fart;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import net.minecraftforge.fart.api.Transformer;

public class SourceFixer implements Transformer {
    enum Config {
        // Uses java style Source file names, this means inner classes get the parent, and it uses a .java extension.
        JAVA;
        // If people care they can PR scala/kotlin/groovy, or map based support
    }

    private static final int MAX_ASM_VERSION = Opcodes.ASM9;
    private final Config config;

    SourceFixer(Config config) {
        this.config = config;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(reader, 0);
        Fixer fixer = new Fixer(writer);

        reader.accept(fixer, 0);

        if (!fixer.madeChange())
            return entry;

        return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
    }

    private class Fixer extends ClassVisitor {
        private boolean madeChange = false;
        private String className = null;
        private boolean hadEntry = false;

        public Fixer(ClassVisitor parent) {
            super(MAX_ASM_VERSION, parent);
        }

        public boolean madeChange() {
            return this.madeChange;
        }

        public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
        }

        public void visitSource(final String source, final String debug) {
            super.visitSource(getSourceName(source), debug);
            hadEntry = true;
        }

        public void visitEnd() {
            if (!hadEntry)
                super.visitSource(getSourceName(null), null);
            super.visitEnd();
        }

        private String getSourceName(String existing) {
            String name = className;
            if (config == Config.JAVA) {
                int idx = name.lastIndexOf('/');
                if (idx != -1)
                    name = name.substring(idx + 1);
                idx = name.indexOf('$');
                if (idx != -1)
                    name = name.substring(0, idx);
                name += ".java";
            }

            if (!name.equals(existing))
                madeChange = true;
            return name;
        }
    }
}
