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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import net.minecraftforge.fart.api.Inheritance;
import net.minecraftforge.fart.api.Inheritance.IClassInfo;
import net.minecraftforge.fart.api.Inheritance.IFieldInfo;
import net.minecraftforge.fart.api.Inheritance.IMethodInfo;
import net.minecraftforge.srgutils.IMappingFile;

import static org.objectweb.asm.Opcodes.*;

class EnhancedRemapper extends Remapper {
    private final Inheritance inh;
    private final IMappingFile map;
    private final Map<String, Optional<MClass>> resolved = new ConcurrentHashMap<>();
    private final Consumer<String> log;

    public EnhancedRemapper(Inheritance inh, IMappingFile map, Consumer<String> log) {
        this.inh = inh;
        this.map = map;
        this.log = log;
    }

    @Override public String mapModuleName(final String name) { return name; } // TODO? None of the mapping formats support this.
    @Override public String mapAnnotationAttributeName(final String descriptor, final String name) { return name; } // TODO: Is this just methods?
    @Override public String mapInvokeDynamicMethodName(final String name, final String descriptor) { return name; } // TODO: Lookup how the JVM resolves this and attempt to resolve it to get the owner?

    @Override
    public String mapMethodName(final String owner, final String name, final String descriptor) {
        return getClass(owner)
            .flatMap(c -> c.getMethod(name, descriptor))
            .map(MClass.MMethod::getMapped)
            .orElse(name);
    }

    @Override // We'll treat this like fields for now, tho at the bytecode level I have no idea what this references
    public String mapRecordComponentName(final String owner, final String name, final String descriptor) {
        return mapFieldName(owner, name, descriptor);
    }

    @Override
    public String mapFieldName(final String owner, final String name, final String descriptor) {
        return getClass(owner)
            .flatMap(c -> c.getField(name, descriptor))
            .map(MClass.MField::getMapped)
            .orElse(name);
    }

    @Override
    public String mapPackageName(final String name) {
        return this.map.remapPackage(name);
    }

    @Override
    public String map(final String name) {
        return getClass(name).map(MClass::getMapped).orElse(map.remapClass(name));
    }

    public String mapParameterName(final String owner, final String methodName, final String methodDescriptor, final int index, final String paramName) {
        return getClass(owner)
            .flatMap(c -> c.getMethod(methodName, methodDescriptor))
            .map(m -> m.mapParameter(index, paramName))
            .orElse(paramName);
    }

    @Override
    public Object mapValue(final Object value) {
        if (value instanceof Handle) {
            // Backport of ASM!327 https://gitlab.ow2.org/asm/asm/-/merge_requests/327
            final Handle handle = (Handle) value;
            final boolean isFieldHandle = handle.getTag() <= Opcodes.H_PUTSTATIC;

            return new Handle(
                    handle.getTag(),
                    this.mapType(handle.getOwner()),
                    isFieldHandle
                            ? this.mapFieldName(handle.getOwner(), handle.getName(), handle.getDesc())
                            : this.mapMethodName(handle.getOwner(), handle.getName(), handle.getDesc()),
                    isFieldHandle ? this.mapDesc(handle.getDesc()) : this.mapMethodDesc(handle.getDesc()),
                    handle.isInterface());
        } else {
            return super.mapValue(value);
        }
    }

    private Optional<MClass> getClass(String cls) {
        if (cls == null || cls.charAt(0) == '[') // Enums values() function invokes 'clone' on the array type.
            return Optional.empty();             // I'm pretty sure that i'd require stupid hacky JVM to allow native array methods to be remapped.
        Optional<MClass> ret = resolved.get(cls);
        if (ret == null) {
            synchronized(cls.intern()) {
                ret = resolved.get(cls);
                if (ret == null) {
                    ret = computeClass(cls);
                    resolved.put(cls, ret);
                }
            }
        }
        return ret;
    }

    private Inheritance getInheritance() {
        return this.inh;
    }

    private IMappingFile getMap() {
        return this.map;
    }

    private Optional<MClass> computeClass(String cls) {
        Optional<? extends IClassInfo> icls = this.getInheritance().getClass(cls);
        IMappingFile.IClass mcls = this.map.getClass(cls);
        if (!icls.isPresent() && mcls == null)
            return Optional.empty();
        return Optional.of(new MClass(icls.orElse(null), mcls));
    }

    private class MClass {
        private final IClassInfo icls;
        private final IMappingFile.IClass mcls;
        private final String mappedName;
        private final List<MClass> parents;
        private final Map<String, Optional<MField>> fields = new ConcurrentHashMap<>();
        private Collection<Optional<MField>> fieldsView = Collections.unmodifiableCollection(fields.values());
        private final Map<String, Optional<MMethod>> methods = new ConcurrentHashMap<>();
        private Collection<Optional<MMethod>> methodsView = Collections.unmodifiableCollection(methods.values());

        MClass(IClassInfo icls, IMappingFile.IClass mcls) {
            if (icls == null && mcls == null)
                throw new IllegalArgumentException("Can't pass in both nulls..");

            this.icls = icls;
            this.mcls = mcls;
            this.mappedName = mcls == null ? EnhancedRemapper.this.getMap().remapClass(icls.getName()) : mcls.getMapped();

            if (icls != null) {
                List<MClass> parents = new ArrayList<>();
                EnhancedRemapper.this.getClass(icls.getSuper()).ifPresent(parents::add);
                icls.getInterfaces().stream().map(EnhancedRemapper.this::getClass).forEach(o -> o.ifPresent(parents::add));
                this.parents = Collections.unmodifiableList(parents);

                icls.getFields().stream().map(f -> new MField(f, mcls == null ? null : mcls.getField(f.getName())))
                    .forEach(f -> fields.put(f.getKey(), Optional.of(f)));

                icls.getMethods().stream().map(m -> new MMethod(m, mcls == null ? null : mcls.getMethod(m.getName(), m.getDescriptor())))
                    .forEach(m -> methods.put(m.getKey(), Optional.of(m)));
            } else {
                this.parents = Collections.emptyList();
                mcls.getFields().stream().map(f -> new MField(null, f)).forEach(f -> fields.put(f.getKey(), Optional.of(f)));
                mcls.getMethods().stream().map(m -> new MMethod(null, m)).forEach(m -> methods.put(m.getKey(), Optional.of(m)));
            }

            for (MClass cls : parents) {
                for (Optional<MField> fldo : cls.getFields()) {
                    if (!fldo.isPresent())
                        continue;

                    MField fld = fldo.orElse(null);
                    Optional<MField> existing = this.fields.get(fld.getKey());
                    if (existing == null || !existing.isPresent()) {
                        /* There are some weird cases where a field will be referenced as if it were owned by the current class,
                         * but it needs a field from the parent. So lets follow the linking spec and pull
                         * down fields from parents.
                         *
                         * https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-5.html#jvms-5.4.3.2
                         */
                        this.fields.put(fld.getKey(), fldo);
                    } else {
                        /* Is there any case where we would ever override an existing field?
                         * We don't inherit renames like we do with methods.
                         * This loop is just to popular the parent field lists so we can
                         * have a cache. Trading memory for faster lookups.
                         *
                         * We could nuke this all, and move this code to the getter
                         */
                    }
                }

                for (Optional<MMethod> mtdo: cls.getMethods()) {
                    if (!mtdo.isPresent())
                        continue;

                    MMethod mtd = mtdo.orElse(null);
                    /* https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-5.html#jvms-5.4.3.3
                     * According to the spec, it does not check access on super classes, but it checks
                     * on interfaces if it is not ACC_PRIVE or ACC_STATIC.
                     *
                     * Here are some examples:
                     *   class A {
                     *     static void foo(){}
                     *   }
                     *   class B extends A {
                     *     static void test(){
                     *       foo();   // Compiles to invokestatic B.foo()Z resolved at runtime to A.foo()Z
                     *       A.foo(); // Compiles to invokestatic A.foo()Z
                     *   }
                     *----------------------------------------------------
                     *   interface A {
                     *     static void foo(){}
                     *   }
                     *   class B extends A {
                     *     static void test(){
                     *       foo();   // Compiles error
                     *       A.foo(); // Compiles to invokestatic A.foo()Z
                     *   }
                     *----------------------------------------------------
                     */
                    if (cls.isInterface() && !mtd.isInterfaceInheritable())
                        continue;


                    Optional<MMethod> existingO = this.methods.get(mtd.getKey());
                    if (existingO == null || !existingO.isPresent()) {
                        /* If there is none existing, then we pull in what we have found from the parents.
                         * This intentionally uses the same object as the parents so that if we have weird edge
                         * cases, we can migrate the mapping transitively.
                         */
                        this.methods.put(mtd.getKey(), mtdo);
                    } else {
                        /* If the method exists, lets check if there is a mapping entry in the parent.
                         * If there is, and our current one doesn't have a map entry directly, then
                         * propagate the mapping.
                         *
                         * This should allow weird interactions, such as a parent method satisfying a
                         * interface's method. And that interface's method having a mapping.
                         * ---------------------------------------------------
                         *   This SHOULD work, because we would get A.foo() without mapping
                         *   Then get B.foo() WITH mapping, and set the forced name to the mapping.
                         *
                         *   class A {
                         *     void foo(){}
                         *   }
                         *   interface B {
                         *     void foo(){}
                         *   }
                         *   class C extends A implements B {}
                         *   MD: B/foo()V B/bar()V
                         */
                        MMethod existing = existingO.orElse(null);
                        if (!existing.hasMapping() && !existing.getName().equals(mtd.getMapped())) {
                            if (!existing.getMapped().equals(mtd.getMapped()))
                                log.accept("Conflicting propagated mapping for " + existing + " from " + mtd + ": " + existing.getMapped() + " -> " + mtd.getMapped());
                            existing.setMapped(mtd.getMapped());
                        }
                        /*
                         * Tho, there is one case I can think of that would be weird.
                         * I need to test.
                         * But something like this might break:
                         *   class A {
                         *     void foo(){}
                         *   }
                         *   interface B {
                         *     void foo(){}
                         *   }
                         *   class C extends A implements B {}
                         *   MD: A/foo()V A/bar()V
                         *
                         *   I think this may break because we would most likely want to propagate
                         *   the mapping to the interface.
                         */
                        else if (!mtd.hasMapping() && !mtd.getName().equals(existing.getMapped())) {
                            if (!mtd.getMapped().equals(existing.getMapped()))
                                log.accept("Conflicting propagated mapping for " + mtd + " from " + existing + ": " + mtd.getMapped() + " -> " + existing.getMapped());
                            mtd.setMapped(existing.getMapped());
                        }
                    }
                }
            }
        }

        public String getName() {
            return this.icls != null ? this.icls.getName() : this.mcls.getOriginal();
        }

        public String getMapped() {
            return this.mappedName;
        }

        public int getAccess() {
            if (this.icls == null)
                return ACC_PRIVATE;
            return this.icls.getAccess();
        }

        public boolean isInterface() {
            return (getAccess() & ACC_INTERFACE) != 0;
        }

        public Collection<Optional<MField>> getFields() {
            return this.fieldsView;
        }

        public Optional<MField> getField(String name, @Nullable String desc) {
            if (desc == null) {
                return this.fields.computeIfAbsent(name, k -> Optional.empty());
            } else {
                Optional<MField> ret = this.fields.get(name + desc);
                if (ret == null) {
                    ret = getField(name, null);
                    this.fields.put(name + desc, ret);
                }
                return ret;
            }
        }

        public Collection<Optional<MMethod>> getMethods() {
            return this.methodsView;
        }

        public Optional<MMethod> getMethod(String name, String desc) {
            return this.methods.computeIfAbsent(name + desc, k -> Optional.empty());
        }

        @Override
        public String toString() {
            return getName();
        }

        public class MField {
            private final IFieldInfo ifld;
            private final IMappingFile.IField mfld;
            private final String mappedName;
            private final String key;

            MField(IFieldInfo ifld, IMappingFile.IField mfld) {
                this.ifld = ifld;
                this.mfld = mfld;
                this.mappedName = mfld == null ? ifld.getName() : mfld.getMapped();
                this.key = getDescriptor() == null ? getName() : getName() + getDescriptor();
            }

            public String getName() {
                return this.ifld != null ? this.ifld.getName() : this.mfld.getOriginal();
            }

            public String getDescriptor() {
                return this.ifld != null ? this.ifld.getDescriptor() : this.mfld.getDescriptor();
            }

            public String getMapped() {
                return this.mappedName;
            }

            public String getKey() {
                return this.key;
            }

            @Override
            public String toString() {
                return MClass.this.getName() + '/' + getName() + ' ' + getDescriptor();
            }
        }

        public class MMethod {
            private final IMethodInfo imtd;
            private final IMappingFile.IMethod mmtd;
            private String mappedName;
            private final String[] params;
            private final String key;

            MMethod(IMethodInfo imtd, IMappingFile.IMethod mmtd) {
                this.imtd = imtd;
                this.mmtd = mmtd;
                if (mmtd != null && !mmtd.getDescriptor().contains("()")) {
                    List<String> tmp = new ArrayList<>();
                    if ((imtd.getAccess() & ACC_STATIC) == 0)
                        tmp.add("this");

                    Type[] args = Type.getArgumentTypes(mmtd.getDescriptor());
                    for (int x = 0; x < args.length; x++) {
                        String name = mmtd.remapParameter(x, null);
                        tmp.add(name);
                        if (args[x].getSize() == 2)
                            tmp.add(name);
                    }

                    this.params = tmp.toArray(new String[tmp.size()]);
                } else {
                    this.params = null;
                }
                this.key = getName() + getDescriptor();
            }

            public String getName() {
                return this.imtd != null ? this.imtd.getName() : this.mmtd.getOriginal();
            }

            public String getDescriptor() {
                return this.imtd != null ? this.imtd.getDescriptor() : this.mmtd.getDescriptor();
            }

            public String getMapped() {
                return mappedName == null ? mmtd == null ? getName() : mmtd.getMapped() : mappedName;
            }

            public String getKey() {
                return this.key;
            }

            public void setMapped(String name) {
                this.mappedName = name;
            }

            public boolean hasMapping() {
                return this.mmtd != null;
            }

            public int getAccess() {
                if (this.imtd == null)
                    return ACC_PRIVATE;
                return this.imtd.getAccess();
            }

            public boolean isInterfaceInheritable() {
                return (getAccess() & (ACC_PRIVATE | ACC_STATIC)) == 0;
            }

            public String mapParameter(int index, String name) {
                return this.params != null && index >= 0 && index < this.params.length ? this.params[index] : name;
            }

            @Override
            public String toString() {
                return MClass.this.getName() + '/' + getName() + getDescriptor();
            }
        }
    }
}
