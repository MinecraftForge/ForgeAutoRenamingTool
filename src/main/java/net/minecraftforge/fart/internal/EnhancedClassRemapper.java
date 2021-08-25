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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;

class EnhancedClassRemapper extends ClassRemapper {
    private final EnhancedRemapper remapper;
    private final RenamingTransformer transformer;

    EnhancedClassRemapper(ClassVisitor classVisitor, EnhancedRemapper remapper, RenamingTransformer transformer) {
        super(classVisitor, remapper);
        this.remapper = remapper;
        this.transformer = transformer;
    }

    private static final Handle META_FACTORY = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
    private static final Handle ALT_META_FACTORY = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "altMetafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);

    @Override
    public MethodVisitor visitMethod(final int access, final String mname, final String mdescriptor, final String msignature, final String[] exceptions) {
        //System.out.println("Method: " + className + '/' + mname + mdescriptor);
        String remappedDescriptor = remapper.mapMethodDesc(mdescriptor);
        MethodVisitor methodVisitor = cv.visitMethod(access, remapper.mapMethodName(className, mname, mdescriptor), remappedDescriptor, remapper.mapSignature(msignature, false), exceptions == null ? null : remapper.mapTypes(exceptions));
        if (methodVisitor == null)
            return null;

        if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
            renameAbstract(access, mname, mdescriptor);

        return new MethodRemapper(methodVisitor, remapper) {
            @Override
            public void visitLocalVariable(final String pname, final String pdescriptor, final String psignature, final Label start, final Label end, final int index) {
                super.visitLocalVariable(renameSnowmen(EnhancedClassRemapper.this.remapper.mapParameterName(className, mname, mdescriptor, index, pname), index), pdescriptor, psignature, start, end, index);
            }

            // Snowmen, added in 1.8.2? rename them names that can exist in source
            private final Map<Integer, Integer> seen = new HashMap<>();
            private String renameSnowmen(String name, int index) {
                if (0x2603 != name.charAt(0))
                    return name;
                int version = seen.computeIfAbsent(index, k -> 0) + 1;
                seen.put(index, version);
                return "lvt_" + index + '_' + version + '_';
            }

            @Override
            public void visitInvokeDynamicInsn(final String name, final String descriptor, final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments) {
                if (META_FACTORY.equals(bootstrapMethodHandle) || ALT_META_FACTORY.equals(bootstrapMethodHandle)) {
                    String owner = Type.getReturnType(descriptor).getInternalName();
                    String odesc = ((Type)bootstrapMethodArguments[0]).getDescriptor();
                                   // First constant argument is "samMethodType - Signature and return type of method to be implemented by the function object."
                                   // index 2 is the signature, but with generic types. Should we use that instead?

                    // We can't call super, because that'd double map the name.
                    // So we do our own mapping.
                    Object[] remappedBootstrapMethodArguments = new Object[bootstrapMethodArguments.length];
                    for (int i = 0; i < bootstrapMethodArguments.length; ++i) {
                      remappedBootstrapMethodArguments[i] = remapper.mapValue(bootstrapMethodArguments[i]);
                    }
                    mv.visitInvokeDynamicInsn(
                        remapper.mapMethodName(owner, name, odesc), // We change this
                        remapper.mapMethodDesc(descriptor),
                        (Handle) remapper.mapValue(bootstrapMethodHandle),
                        remappedBootstrapMethodArguments);
                    return;
                }

                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        };
    }

    private void renameAbstract(int access, String name, String descriptor) {
        Type[] types = Type.getArgumentTypes(descriptor);
        if (types.length == 0)
            return;

        List<String> names = new ArrayList<>();
        int i = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type type : types) {
            names.add(remapper.mapParameterName(className, name, descriptor, i, "var" + i));
            i += type.getSize();
        }

        transformer.storeNames(
            remapper.mapType(className),
            remapper.mapMethodName(className, name, descriptor),
            remapper.mapMethodDesc(descriptor),
            names
        );
    }
}
