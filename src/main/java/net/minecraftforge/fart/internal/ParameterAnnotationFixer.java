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

import static org.objectweb.asm.Opcodes.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraftforge.fart.api.Transformer;

public final class ParameterAnnotationFixer implements Transformer {
    private final Consumer<String> log;
    private final Consumer<String> debug;

    public ParameterAnnotationFixer(Consumer<String> log, Consumer<String> debug) {
        this.log = log;
        this.debug = debug;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        final ClassReader reader = new ClassReader(entry.getData());
        final ClassWriter writer = new ClassWriter(reader, 0);
        final ClassNode node = new ClassNode();
        reader.accept(new Visitor(node), 0);
        node.accept(writer);
        return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
    }

    private class Visitor extends ClassVisitor {
        private final ClassNode node;

        public Visitor(ClassNode cn) {
            super(RenamerImpl.MAX_ASM_VERSION, cn);
            this.node = cn;
        }

        private void debug(String message) {
            debug.accept(message);
        }

        private void log(String message) {
            log.accept(message);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();

            Type[] syntheticParams = getExpectedSyntheticParams(node);
            if (syntheticParams != null) {
                for (MethodNode mn : node.methods) {
                    if (mn.name.equals("<init>"))
                        processConstructor(node, mn, syntheticParams);
                }
            }
        }

        /**
        * Checks if the given class might have synthetic parameters in the
        * constructor. There are two cases where this might happen:
        * <ol>
        * <li>If the given class is an inner class, the first parameter is the
        * instance of the outer class.</li>
        * <li>If the given class is an enum, the first parameter is the enum
        * constant name and the second parameter is its ordinal.</li>
        * </ol>
        *
        * @return An array of types for synthetic parameters if the class can have
        *         synthetic parameters, otherwise null.
        */
        private Type[] getExpectedSyntheticParams(ClassNode cls) {
            // Check for enum
            // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/comp/Lower.java#l2866
            if ((cls.access & ACC_ENUM) != 0) {
                debug("  Considering " + cls.name + " for extra parameter annotations as it is an enum");
                return new Type[] { Type.getObjectType("java/lang/String"), Type.INT_TYPE };
            }

            // Check for inner class
            InnerClassNode info = null;
            for (InnerClassNode node : cls.innerClasses) { // note: cls.innerClasses is never null
                if (node.name.equals(cls.name)) {
                    info = node;
                    break;
                }
            }
            // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/code/Symbol.java#l398
            if (info == null) {
                debug("  Not considering " + cls.name + " for extra parameter annotations as it is not an inner class");
                return null; // It's not an inner class
            }
            if ((info.access & (ACC_STATIC | ACC_INTERFACE)) != 0) {
                debug("  Not considering " + cls.name + " for extra parameter annotations as is an interface or static");
                return null; // It's static or can't have a constructor
            }

            // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/jvm/ClassReader.java#l2011
            if (info.innerName == null) {
                debug("  Not considering " + cls.name + " for extra parameter annotations as it is annonymous");
                return null; // It's an anonymous class
            }

            if (info.outerName == null) {
                int idx = cls.name.lastIndexOf('$');
                if (idx == -1) {
                    debug("  Not cosidering " + cls.name + " for extra parameter annotations as it does not appear to be an inner class");
                    return null;
                }
                debug("  Considering " + cls.name + " for extra parameter annotations as its name appears to be an inner class of " + cls.name.substring(0, idx));
                return new Type[] { Type.getObjectType(cls.name.substring(0, idx)) };
            }

            debug("  Considering " + cls.name + " for extra parameter annotations as it is an inner class of " + info.outerName);
            return new Type[] { Type.getObjectType(info.outerName) };
        }

        /**
        * Removes the parameter annotations for the given synthetic parameters,
        * if there are parameter annotations and the synthetic parameters exist.
        */
        private void processConstructor(ClassNode cls, MethodNode mn, Type[] syntheticParams) {
            String methodInfo = mn.name + mn.desc + " in " + cls.name;
            Type[] params = Type.getArgumentTypes(mn.desc);

            if (beginsWith(params, syntheticParams)) {
                mn.visibleParameterAnnotations = process(methodInfo, "RuntimeVisibleParameterAnnotations", params.length, syntheticParams.length, mn.visibleParameterAnnotations);
                mn.invisibleParameterAnnotations = process(methodInfo, "RuntimeInvisibleParameterAnnotations", params.length, syntheticParams.length, mn.invisibleParameterAnnotations);
                // ASM uses this value, not the length of the array
                // Note that this was added in ASM 6.1
                if (mn.visibleParameterAnnotations != null)
                    mn.visibleAnnotableParameterCount = mn.visibleParameterAnnotations.length;
                if (mn.invisibleParameterAnnotations != null)
                    mn.invisibleAnnotableParameterCount = mn.invisibleParameterAnnotations.length;
            } else
                log("Unexpected lack of synthetic args to the constructor: expected " + Arrays.toString(syntheticParams) + " at the start of " + methodInfo);
        }

        private boolean beginsWith(Type[] values, Type[] prefix) {
            if (values.length < prefix.length)
                return false;
            for (int i = 0; i < prefix.length; i++) {
                if (!values[i].equals(prefix[i]))
                    return false;
            }
            return true;
        }

        /**
        * Removes annotation nodes corresponding to synthetic parameters, after
        * the existence of synthetic parameters has already been checked.
        *
        * @param methodInfo
        *            A description of the method, for logging
        * @param attributeName
        *            The name of the attribute, for logging
        * @param numParams
        *            The number of parameters in the method
        * @param numSynthetic
        *            The number of synthetic parameters (should not be 0)
        * @param annotations
        *            The current array of annotation nodes, may be null
        * @return The new array of annotation nodes, may be null
        */
        private List<AnnotationNode>[] process(String methodInfo, String attributeName, int numParams, int numSynthetic, List<AnnotationNode>[] annotations) {
            if (annotations == null) {
                debug("    " + methodInfo + " does not have a " + attributeName + " attribute");
                return null;
            }

            int numAnnotations = annotations.length;
            if (numParams == numAnnotations) {
                log("Found extra " + attributeName + " entries in " + methodInfo + ": removing " + numSynthetic);
                return Arrays.copyOfRange(annotations, numSynthetic, numAnnotations);
            } else if (numParams == numAnnotations - numSynthetic) {
                debug("Number of " + attributeName + " entries in " + methodInfo + " is already as we want");
                return annotations;
            } else {
                log("Unexpected number of " + attributeName + " entries in " + methodInfo + ": " + numAnnotations);
                return annotations;
            }
        }
    }
}
