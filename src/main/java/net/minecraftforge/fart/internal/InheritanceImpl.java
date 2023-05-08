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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraftforge.fart.api.Inheritance;

public class InheritanceImpl implements Inheritance {
    private final Consumer<String> log;
    private Map<String, File> sources = new HashMap<>();
    private Map<String, Optional<ClassInfo>> classes = new ConcurrentHashMap<>();
    private ClassLoader loader = this.getClass().getClassLoader();

    public InheritanceImpl(Consumer<String> log) {
        this.log = log;
    }

    @Override
    public void setClassLoader(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public void addLibrary(File path) {
        try (ZipFile jar = new ZipFile(path)) {
            Util.forZip(jar, e -> {
                if (!e.getName().endsWith(".class") || e.getName().startsWith("META-INF"))
                    return;
                String name = e.getName();
                name = name.substring(0, name.length() - 6);
                sources.putIfAbsent(name, path);
            });
        } catch (IOException e) {
            throw new RuntimeException("Could not add library: " + path.getAbsolutePath(), e);
        }
    }

    @Override
    public Optional<? extends IClassInfo> getClass(String cls) {
        return classes.computeIfAbsent(cls, this::computeClassInfo);
    }

    @Override
    public void addClass(String name, byte[] value) {
        this.classes.computeIfAbsent(name, k -> Optional.of(new ClassInfo(value)));
    }

    private Optional<ClassInfo> computeClassInfo(String name) {
        File source = sources.get(name);
        if (source != null) {
            try (ZipFile zf = new ZipFile(source)) {
                ZipEntry entry = zf.getEntry(name + ".class");
                if (entry == null)
                    throw new IllegalStateException("Could not get " + name + ".class entry in " + source.getAbsolutePath());
                byte[] data = Util.toByteArray(zf.getInputStream(entry));
                return Optional.of(new ClassInfo(data));
            } catch (IOException e) {
                throw new RuntimeException("Could not get data to compute class info in file: " + source.getAbsolutePath(), e);
            }
        } else {
            try {
                Class<?> cls = Class.forName(name.replace('/', '.'), false, this.loader);
                return Optional.of(new ClassInfo(cls));
            } catch (ClassNotFoundException ex) {
                log.accept("Cant Find Class: " + name);
                return Optional.empty();
            }
        }
    }

    private static class ClassInfo implements IClassInfo {
        private final String name;
        private final Access access;
        private final String superName;
        private final List<String> interfaces;
        private final Map<String, FieldInfo> fields;
        private Collection<FieldInfo> fieldsView;
        private final Map<String, MethodInfo> methods;
        private Collection<MethodInfo> methodsView;

        ClassInfo(byte[] data) {
            ClassReader reader = new ClassReader(data);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE);

            this.name = node.name;
            this.access = new Access(node.access);
            this.superName = node.superName;
            this.interfaces = node.interfaces.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(node.interfaces);

            if (!node.methods.isEmpty())
                this.methods = Collections.unmodifiableMap(node.methods.stream().map(MethodInfo::new)
                    .collect(Collectors.toMap(m -> m.getName() + m.getDescriptor(), Function.identity())));
            else
                this.methods = null;


            if (!node.fields.isEmpty())
                this.fields = Collections.unmodifiableMap(node.fields.stream().map(FieldInfo::new)
                    .collect(Collectors.toMap(FieldInfo::getName, Function.identity())));
            else
                this.fields = null;
        }

        ClassInfo(Class<?> node) {
            this.name = Util.nameToBytecode(node);
            this.access = new Access(node.getModifiers());
            this.superName = Util.nameToBytecode(node.getSuperclass());
            this.interfaces = Collections.unmodifiableList(Arrays.stream(node.getInterfaces())
                .map(c -> Util.nameToBytecode(c)).collect(Collectors.toList()));

            Map<String, MethodInfo> mtds = Stream.concat(
                Arrays.stream(node.getConstructors()).map(MethodInfo::new),
                Arrays.stream(node.getDeclaredMethods()).map(MethodInfo::new)
            ).collect(Collectors.toMap(m -> m.getName() + m.getDescriptor(), Function.identity()));

            this.methods = mtds.isEmpty() ? null : Collections.unmodifiableMap(mtds);

            Field[] flds = node.getDeclaredFields();
            if (flds != null && flds.length > 0) {
                this.fields = Collections.unmodifiableMap(Arrays.asList(flds).stream().map(FieldInfo::new)
                    .collect(Collectors.toMap(FieldInfo::getName, Function.identity())));
            } else
                this.fields = null;
        }

        @Override
        public String getName() {
            return name;
        }
        @Override
        public String getSuper() {
            return superName;
        }
        @Override
        public int getAccess() {
            return access.getValue();
        }

        public Access getAccessLevel() {
            return this.access;
        }

        @Override
        public Collection<String> getInterfaces() {
            return interfaces;
        }

        @Override
        public Collection<? extends IFieldInfo> getFields() {
            if (fieldsView == null)
                fieldsView = fields == null ? Collections.emptyList() : Collections.unmodifiableCollection(fields.values());
            return fieldsView;
        }

        @Override
        public Optional<? extends IFieldInfo> getField(String name) {
            return fields == null ? Optional.empty() : Optional.ofNullable(this.fields.get(name));
        }

        @Override
        public Collection<? extends IMethodInfo> getMethods() {
            if (methodsView == null)
                methodsView = methods == null ? Collections.emptyList() : Collections.unmodifiableCollection(methods.values());
            return methodsView;
        }

        @Override
        public Optional<? extends IMethodInfo> getMethod(String name, String desc) {
            return methods == null ? Optional.empty() : Optional.ofNullable(methods.get(name + " " + desc));
        }

        @Override
        public String toString() {
            return getAccessLevel().toString() + ' ' + getName();
        }

        private class FieldInfo implements IFieldInfo {
            private final String name;
            private final String desc;
            private final Access access;

            public FieldInfo(FieldNode node) {
                this.name = node.name;
                this.desc = node.desc;
                this.access = new Access(node.access);
            }

            public FieldInfo(Field node) {
                this.name = node.getName();
                this.desc = Type.getType(node.getType()).getDescriptor();
                this.access = new Access(node.getModifiers());
            }

            @Override
            public int getAccess() {
                return access.getValue();
            }

            public Access getAccessLevel() {
                return this.access;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescriptor() {
                return desc;
            }

            @Override
            public String toString() {
                return getAccessLevel().toString() + ' ' + ClassInfo.this.getName() + '/' + getName() + ' ' + getDescriptor();
            }
        }

        private class MethodInfo implements IMethodInfo {
            private final String name;
            private final String desc;
            private final Access access;

            MethodInfo(MethodNode node) {
                this.name = node.name;
                this.desc = node.desc;
                this.access = new Access(node.access);
            }

            MethodInfo(Method node) {
                this.name = node.getName();
                this.desc = Type.getMethodDescriptor(node);
                this.access = new Access(node.getModifiers());
            }

            MethodInfo(Constructor<?> node) {
                this.name = "<init>";
                this.desc = Type.getConstructorDescriptor(node);
                this.access = new Access(node.getModifiers());
            }

            @Override
            public int getAccess() {
                return access.getValue();
            }

            public Access getAccessLevel() {
                return this.access;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescriptor() {
                return desc;
            }

            public String toString() {
                return getAccessLevel().toString() + ' ' + ClassInfo.this.getName() + '/' + getName() + getDescriptor();
            }
        }
    }

    private static class Access {
        private static int[] ACC = new int[23];
        private static String[] NAME = new String[23];
        static {
            int idx = 0;
            put(idx++, ACC_PUBLIC,      "public");
            put(idx++, ACC_PRIVATE,     "private");
            put(idx++, ACC_PROTECTED,   "protected");
            put(idx++, ACC_STATIC,      "static");
            put(idx++, ACC_FINAL,       "final");
            put(idx++, ACC_SUPER,       "super");
            put(idx++, ACC_SYNCHRONIZED,"synchronized");
            put(idx++, ACC_OPEN,        "open");
            put(idx++, ACC_TRANSITIVE,  "transitive");
            put(idx++, ACC_VOLATILE,    "volatile");
            put(idx++, ACC_BRIDGE,      "bridge");
            put(idx++, ACC_STATIC_PHASE,"static_phase");
            put(idx++, ACC_VARARGS,     "varargs");
            put(idx++, ACC_TRANSIENT,   "transient");
            put(idx++, ACC_NATIVE,      "native");
            put(idx++, ACC_INTERFACE,   "interface");
            put(idx++, ACC_ABSTRACT,    "abstract");
            put(idx++, ACC_STRICT,      "strict");
            put(idx++, ACC_SYNTHETIC,   "synthetic");
            put(idx++, ACC_ANNOTATION , "annotation");
            put(idx++, ACC_ENUM,        "enum");
            put(idx++, ACC_MANDATED,    "mandated");
            put(idx++, ACC_MODULE,      "module");
        }
        private static void put(int idx, int acc, String name) {
            ACC[idx] = acc;
            NAME[idx] = name;
        }

        private final int value;
        private String toString;

        public Access(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }

        @Override
        public String toString() {
            if (toString == null) {
                List<String> ret = new ArrayList<>();
                for (int x = 0; x < ACC.length; x++) {
                    if ((value & ACC[x]) != 0)
                        ret.add(NAME[x]);
                }
                if (ret.isEmpty())
                    toString = "default";
                else
                    toString = ret.stream().collect(Collectors.joining(" "));
            }
            return toString;
        }
    }
}
