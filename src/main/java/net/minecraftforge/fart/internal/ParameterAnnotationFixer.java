/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.internal;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

import static org.objectweb.asm.Opcodes.*;

public final class ParameterAnnotationFixer extends OptionalChangeTransformer {
    public static final ParameterAnnotationFixer INSTANCE = new ParameterAnnotationFixer();

    private ParameterAnnotationFixer() {
        super(Fixer::new);
    }

    private static class Fixer extends OptionalChangeTransformer.ClassFixer {
        private String name;
        private boolean isEnum;
        private String outerName;

        public Fixer(ClassVisitor parent) {
            super(parent);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.name = name;
            if ((access & ACC_ENUM) != 0)
                this.isEnum = true;

            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if ((access & (ACC_STATIC | ACC_INTERFACE)) == 0 && this.name.equals(name) && innerName != null) {
                // We know it is an inner class here
                if (outerName == null) {
                    int idx = name.lastIndexOf('$');
                    if (idx != -1)
                        this.outerName = name.substring(0, idx);
                } else {
                    this.outerName = outerName;
                }
            }

            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

            if (!name.equals("<init>"))
                return methodVisitor;

            Type[] syntheticParams = null;

            if (this.isEnum) {
                syntheticParams = new Type[]{Type.getObjectType("java/lang/String"), Type.INT_TYPE};
            } else if (this.outerName != null) {
                syntheticParams = new Type[]{Type.getObjectType(this.outerName)};
            }

            if (syntheticParams == null)
                return methodVisitor;

            Type[] argumentTypes = Type.getArgumentTypes(descriptor);
            return beginsWith(argumentTypes, syntheticParams) ? new MethodFixer(argumentTypes.length, syntheticParams.length, methodVisitor) : methodVisitor;
        }

        private static boolean beginsWith(Type[] values, Type[] prefix) {
            if (values.length < prefix.length)
                return false;
            for (int i = 0; i < prefix.length; i++) {
                if (!values[i].equals(prefix[i]))
                    return false;
            }
            return true;
        }

        private class MethodFixer extends MethodVisitor {
            private final int argumentsLength;
            private final int numSynthetic;
            private final AnnotationHolder[] annotations;
            private int parameterAnnotationCount;
            private boolean visibleParamAnnotations;
            private boolean hasParamAnnotation;

            MethodFixer(int argumentsLength, int numSynthetic, MethodVisitor methodVisitor) {
                super(RenamerImpl.MAX_ASM_VERSION, methodVisitor);
                this.argumentsLength = argumentsLength;
                this.numSynthetic = numSynthetic;
                this.annotations = new AnnotationHolder[argumentsLength];
            }

            @Override
            public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
                this.parameterAnnotationCount = parameterCount;
                this.visibleParamAnnotations = visible;

                // Don't call super yet
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                this.hasParamAnnotation = true;
                AnnotationNode node = new AnnotationNode(RenamerImpl.MAX_ASM_VERSION, descriptor);
                this.annotations[parameter] = new AnnotationHolder(parameter, descriptor, visible, node);
                return node; // Don't call super yet
            }

            @Override
            public void visitEnd() {
                int offset = 0;
                if (this.hasParamAnnotation && this.parameterAnnotationCount == this.argumentsLength) {
                    // The ProGuard bug only applies if parameterAnnotationCount and the length of the argument types exactly match.
                    // See https://github.com/Guardsquare/proguard/blob/b0db59bc59fca1fc3f0083dd2e354a71b544d77c/core/src/proguard/classfile/io/ProgramClassReader.java#L745 for the bug.
                    // parameterAnnotationCount is the number of parameter annotations read from the bytecode, whereas the length of the argument types is the number of parameters.
                    // The ProGuard bug always forcefully reassigns the parameterCount from bytecode to the length of the argument types alongside the other problematic bits of code.
                    // If it didn't do that, then we know the buggy ProGuard version is not in use.

                    offset = this.numSynthetic;
                }

                if (offset != 0)
                    Fixer.this.madeChange = true;

                // Offer the data to the parent visitor; potentially with our fixes applied
                super.visitAnnotableParameterCount(this.parameterAnnotationCount - offset, this.visibleParamAnnotations);
                for (AnnotationHolder holder : this.annotations) {
                    if (holder != null) {
                        int parameter = holder.parameter - offset;
                        if (parameter >= 0) // Although synthetic parameters should never have annotations, let's ensure against out-of-bounds just in case
                            holder.node.accept(super.visitParameterAnnotation(parameter, holder.descriptor, holder.visible));
                    }
                }

                super.visitEnd();
            }
        }
    }

    private static class AnnotationHolder {
        final int parameter;
        final String descriptor;
        final boolean visible;
        final AnnotationNode node;

        AnnotationHolder(int parameter, String descriptor, boolean visible, AnnotationNode node) {
            this.parameter = parameter;
            this.descriptor = descriptor;
            this.visible = visible;
            this.node = node;
        }
    }
}
