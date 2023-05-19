package net.minecraftforge.fart.internal;

import net.minecraftforge.fart.api.ClassProvider;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

class ClassProviderImpl implements ClassProvider {
    /**
     * A list of the open (ZIP) filesystems.
     */
    private final List<FileSystem> fileSystems;
    /**
     * Holds a map of ZIP entry name / full classname -> path to the class file.
     * Always uses {@code /} for path delimiters.
     */
    private final Map<String, Path> sources;
    /**
     * Only holds classes explicitly added through the builder with their raw class bytes.
     */
    private final Map<String, Optional<? extends IClassInfo>> classInfos;
    /**
     * Optionally caches all class infos returned by this implementation, if not null.
     */
    @Nullable
    private final Map<String, Optional<? extends IClassInfo>> classCache;

    ClassProviderImpl(List<FileSystem> fileSystems, Map<String, Path> sources, Map<String, Optional<? extends IClassInfo>> classInfos, boolean cacheAll) {
        this.fileSystems = Collections.unmodifiableList(fileSystems);
        this.sources = Collections.unmodifiableMap(sources);
        this.classInfos = Collections.unmodifiableMap(classInfos);
        this.classCache = cacheAll ? new ConcurrentHashMap<>() : null;
    }

    @Override
    public Optional<? extends IClassInfo> getClass(String name) {
        return this.classCache != null ? this.classCache.computeIfAbsent(name, this::computeClassInfo) : computeClassInfo(name);
    }

    private Optional<? extends IClassInfo> computeClassInfo(String name) {
        if (this.classInfos.containsKey(name))
            return this.classInfos.get(name);

        Path source = this.sources.get(name);

        if (source == null)
            return Optional.empty();

        try {
            byte[] data = Util.toByteArray(Files.newInputStream(source));
            return Optional.of(new ClassInfo(data));
        } catch (IOException e) {
            throw new RuntimeException("Could not get data to compute class info in file: " + source.toAbsolutePath(), e);
        }
    }

    @Override
    public void close() throws IOException {
        for (FileSystem fs : this.fileSystems) {
            fs.close();
        }
    }

    static class ClassInfo implements IClassInfo {
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
        @Nullable
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
