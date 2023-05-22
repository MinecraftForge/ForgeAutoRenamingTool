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

package net.minecraftforge.fart.api;

import net.minecraftforge.fart.internal.ClassLoaderClassProvider;
import net.minecraftforge.fart.internal.ClassProviderBuilderImpl;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * Provides basic information about classes including inheritance, fields, and methods.
 */
public interface ClassProvider extends Closeable {
    /**
     * Creates a default instance of a {@link ClassProvider.Builder}.
     * <p>
     * The default supported library paths are ZIP files and directories.
     * Upon calling {@link Builder#addLibrary(Path)}, the path will be walked for all class files and stored.
     * Like a class path, entries added earlier take precedence over later entries with the same name.
     */
    public static Builder builder() {
        return new ClassProviderBuilderImpl();
    }

    /**
     * Creates a class provider which reads class data from the provided library paths.
     * All queried class infos will be cached for subsequent queries.
     * <p>
     * The default supported library paths are ZIP files and directories.
     * Each path will be walked for all class files and stored in order.
     * Like a class path, entries added earlier take precedence over later entries with the same name.
     *
     * @param paths the paths to read from
     * @see #builder()
     */
    static ClassProvider fromPaths(Path... paths) {
        Builder builder = builder().shouldCacheAll(true);

        for (Path path : paths) {
            builder.addLibrary(path);
        }

        return builder.build();
    }

    /**
     * Creates a class provider which reads class data from the default classloader that loaded this class.
     */
    static ClassProvider fromJvmClasspath() {
        return new ClassLoaderClassProvider(null);
    }

    /**
     * Creates a class provider which reads class data from the provided classloader,
     * or the classloader of this class if null.
     *
     * @param classLoader the classloader to read from, or {@code null} for the default JVM classpath
     */
    static ClassProvider fromClassLoader(@Nullable ClassLoader classLoader) {
        return new ClassLoaderClassProvider(classLoader);
    }

    /**
     * Queries the class information from this class path.
     * An empty optional will be returned if the class cannot be found.
     *
     * @param cls the fully resolved classname, see {@link Type#getInternalName()}
     * @return the optional class information
     */
    Optional<? extends IClassInfo> getClass(String cls);

    /**
     * A {@code ClassProvider.Builder} is used to configure and construct a {@link ClassProvider}.
     */
    public interface Builder {
        /**
         * Adds a library to the sources of this builder.
         * The implementation is free to accept or not accept paths of any kind.
         * Libraries are used when querying class information.
         *
         * @param path the path object
         * @return this builder
         */
        Builder addLibrary(Path path);

        /**
         * Adds class bytes for a class to this builder.
         * This may be an optional operation depending on the implementation.
         *
         * @param cls the fully resolved classname, see {@link Type#getInternalName()}
         * @param data the class bytes
         * @return this builder
         */
        Builder addClass(String cls, byte[] data);

        /**
         * Sets whether this class provider should cache all generated class infos.
         * This can speed up subsequent accesses to the same class info, but will use more memory.
         *
         * @return this builder
         */
        Builder shouldCacheAll(boolean value);

        /**
         * Builds the {@link ClassProvider} instance based on this configured builder.
         *
         * @return the built {@link ClassProvider}
         */
        ClassProvider build();
    }

    /**
     * Holds basic information about a class.
     */
    public interface IClassInfo {
        /**
         * Returns the access flags of this class.
         *
         * @see ClassNode#access
         * @see Class#getModifiers()
         */
        int getAccess();

        /**
         * Returns the internal name of this class.
         *
         * @see Type#getInternalName()
         */
        String getName();

        /**
         * Returns the internal name of the superclass,
         * or null if there is no superclass.
         *
         * @see ClassNode#superName
         * @see Class#getSuperclass()
         */
        @Nullable
        String getSuper();

        /**
         * Returns a list of interfaces this class implements.
         */
        Collection<String> getInterfaces();

        /**
         * Returns all the fields declared in this class.
         */
        Collection<? extends IFieldInfo> getFields();

        /**
         * Queries a field based on its field name.
         *
         * @param name the field name
         * @return the field, or an empty optional if it doesn't exist
         */
        Optional<? extends IFieldInfo> getField(String name); //TODO: Desc?

        /**
         * Returns all the methods declared in this class.
         */
        Collection<? extends IMethodInfo> getMethods();

        /**
         * Queries a method based on its method name and descriptor.
         *
         * @param name the method name
         * @param desc the method descriptor
         * @return the method, or an empty optional if it doesn't exist
         */
        Optional<? extends IMethodInfo> getMethod(String name, String desc);
    }

    /**
     * Holds basic information about a field contained in a class.
     */
    public interface IFieldInfo {
        /**
         * Returns the access flags of this field.
         *
         * @see FieldNode#access
         * @see Field#getModifiers()
         */
        int getAccess();

        /**
         * Returns the name of this field.
         */
        String getName();

        /**
         * Returns the descriptor of this field.
         *
         * @see Type#getDescriptor()
         */
        String getDescriptor();
    }

    /**
     * Holds basic information about a method contained in a class.
     */
    public interface IMethodInfo {
        /**
         * Returns the access flags of this method.
         *
         * @see MethodNode#access
         * @see Method#getModifiers()
         */
        int getAccess();

        /**
         * Returns the name of this method.
         */
        String getName();

        /**
         * Returns the descriptor of this method.
         *
         * @see Type#getDescriptor()
         */
        String getDescriptor();
    }
}
