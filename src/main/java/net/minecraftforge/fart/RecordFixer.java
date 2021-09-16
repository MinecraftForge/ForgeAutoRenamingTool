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
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.tree.ClassNode;

class RecordFixer extends OptionalChangeTransformer {
    protected RecordFixer() {
        super(Fixer::new);
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassNode node = new ClassNode();
        ClassFixer fixer = fixerFactory.apply(node);

        reader.accept(fixer, 0);

        if (!fixer.madeChange())
            return entry;

        node.accept(writer);

        return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
    }

    private static class Fixer extends ClassFixer {
        private boolean isRecord;
        private boolean hasRecordComponents;
        private boolean addingRecordComponents;

        public Fixer(ClassVisitor parent) {
            super(parent);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.isRecord = "java/lang/Record".equals(superName);
            if (isRecord && (access & Opcodes.ACC_RECORD) == 0) {
                // Add back the record flag if this class extends from Record but is missing it because of Proguard stripping
                access |= Opcodes.ACC_RECORD;
                this.madeChange = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            this.hasRecordComponents = true;
            return super.visitRecordComponent(name, descriptor, signature);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (isRecord && (addingRecordComponents || !hasRecordComponents) && (access & Opcodes.ACC_PRIVATE) != 0 && (access & Opcodes.ACC_FINAL) != 0 && (access & Opcodes.ACC_STATIC) == 0) {
                this.madeChange = true;
                this.addingRecordComponents = true;
                // Manually add the record component back if this class doesn't have any
                this.visitRecordComponent(name, descriptor, signature);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }
    }
}
