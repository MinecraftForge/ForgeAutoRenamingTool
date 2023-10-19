/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.fart.internal;

import net.minecraftforge.fart.api.SourceFixerConfig;
import org.objectweb.asm.ClassVisitor;

public final class SourceFixer extends OptionalChangeTransformer {

    public SourceFixer(SourceFixerConfig config) {
        super(parent -> new Fixer(config, parent));
    }

    private static class Fixer extends ClassFixer {
        private final SourceFixerConfig config;
        private String className = null;
        private boolean hadEntry = false;

        public Fixer(SourceFixerConfig config, ClassVisitor parent) {
            super(parent);
            this.config = config;
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
            if (config == SourceFixerConfig.JAVA) {
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
