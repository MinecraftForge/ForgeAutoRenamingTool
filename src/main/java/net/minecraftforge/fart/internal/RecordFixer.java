/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;

public class RecordFixer extends OptionalChangeTransformer {
    public static final RecordFixer INSTANCE = new RecordFixer();

    private RecordFixer() {
        super(Fixer::new);
    }

    private static class Fixer extends ClassFixer {
        private Map<String, Entry> components;
        private boolean isRecord;
        private boolean hasRecordComponents;

        public Fixer(ClassVisitor parent) {
            super(parent);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.isRecord = "java/lang/Record".equals(superName);
            if (isRecord && ((access & Opcodes.ACC_RECORD) == 0)) {
                // ASM Uses this to determine if it should write the records components at all, which is necessary even if empty.
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
            // We want any fields that are final and not static. Proguard sometimes increases the visibility of record component fields to be higher than private.
            // These fields still need to have record components generated, so we need to ignore ACC_PRIVATE.
            if (isRecord && (access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) == Opcodes.ACC_FINAL) {
                // Make sure the visibility gets set back to private
                int newAccess = access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PRIVATE;
                if (newAccess != access) {
                    this.madeChange = true;
                    access = newAccess;
                }
                // Manually add the record component back if this class doesn't have any
                if (components == null)
                    components = new LinkedHashMap<String, Entry>();
                components.put(name + descriptor, new Entry(name, descriptor, signature));
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public void visitEnd() {
            if (isRecord && !hasRecordComponents && components != null) {
                for (Entry entry : this.components.values()) {
                    this.visitRecordComponent(entry.name, entry.descriptor, entry.signature);
                    this.madeChange = true;
                }
            }
        }

        private static class Entry {
            private final String name;
            private final String descriptor;
            private final String signature;
            private Entry(String name, String descriptor, String signature) {
                this.name = name;
                this.descriptor = descriptor;
                this.signature = signature;
            }

            @Override
            public String toString() {
                return "[Name: " + name  + ", Desc: "  + descriptor + ", Sig: " + signature + "]";
            }
        }
    }
}
