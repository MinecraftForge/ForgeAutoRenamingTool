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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.util.function.Function;

abstract class OptionalChangeTransformer implements Transformer {
    protected final Function<ClassVisitor, ClassFixer> fixerFactory;

    protected OptionalChangeTransformer(Function<ClassVisitor, ClassFixer> fixerFactory) {
        this.fixerFactory = fixerFactory;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassFixer fixer = fixerFactory.apply(writer);

        reader.accept(fixer, 0);

        if (!fixer.madeChange())
            return entry;

        return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
    }

    protected abstract static class ClassFixer extends ClassVisitor {
        protected boolean madeChange = false;

        protected ClassFixer(ClassVisitor parent) {
            this(RenamerImpl.MAX_ASM_VERSION, parent);
        }

        protected ClassFixer(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        public boolean madeChange() {
            return this.madeChange;
        }
    }
}
