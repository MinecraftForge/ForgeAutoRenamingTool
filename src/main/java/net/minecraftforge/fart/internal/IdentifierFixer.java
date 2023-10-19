/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.fart.internal;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.fart.api.IdentifierFixerConfig;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public final class IdentifierFixer extends OptionalChangeTransformer {
    public IdentifierFixer(IdentifierFixerConfig config) {
        super(parent -> new Fixer(config, parent));
    }

    private static class Fixer extends ClassFixer {
        private final IdentifierFixerConfig config;

        public Fixer(IdentifierFixerConfig config, ClassVisitor parent) {
            super(parent);
            this.config = config;
        }

        @Override
        public final MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
            MethodVisitor parent = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(RenamerImpl.MAX_ASM_VERSION, parent) {
                @Override
                public void visitLocalVariable(final String pname, final String pdescriptor, final String psignature, final Label start, final Label end, final int index) {
                    String newName = fixName(pname, index);
                    super.visitLocalVariable(newName, pdescriptor, psignature, start, end, index);
                }

                private final Map<Integer, Integer> seen = new HashMap<>();
                private String fixName(String name, int index) {
                    boolean valid = true;
                    if (name.isEmpty()) {
                        valid = false;
                    } else if (config == IdentifierFixerConfig.SNOWMEN) {
                        // Snowmen, added in 1.8.2? rename them names that can exist in source
                        if ((char)0x2603 == name.charAt(0))
                            valid = false;
                    } else {
                        if (!Character.isJavaIdentifierStart(name.charAt(0)))
                            valid = false;
                        else  {
                            for (int x = 1; x < name.length(); x++) {
                                if (!Character.isJavaIdentifierPart(name.charAt(x))) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                    }
                    if (valid)
                        return name;

                    Fixer.this.madeChange = true;

                    int version = seen.computeIfAbsent(index, k -> 0) + 1;
                    seen.put(index, version);
                    return "lvt_" + index + '_' + version + '_';
                }
            };
        }
    }
}
