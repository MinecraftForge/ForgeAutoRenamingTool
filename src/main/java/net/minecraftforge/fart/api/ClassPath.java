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

import net.minecraftforge.fart.internal.ClassPathImpl;
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
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Holds basic information about classes including inheritance, fields, and methods.
 * <p>
 * This class path stores library paths and class information objects.
 */
public interface ClassPath extends Closeable {
    /**
     * Creates a default instance which logs to {@link System#out}.
     * <p>
     * The default supported library paths are ZIP files and directories.
     * Upon calling {@link #addLibrary(Path)}, the path will be walked for all class files and stored.
     * Like a class path, entries added earlier take precedence over later entries with the same name.
     */
    static ClassPath create() {
        return new ClassPathImpl(System.out::println);
    }

    /**
     * Creates a default instance which logs to the provided consumer.
     * <p>
     * The default supported library paths are ZIP files and directories.
     * Upon calling {@link #addLibrary(Path)}, the path will be walked for all class files and stored.
     * Like a class path, entries added earlier take precedence over later entries with the same name.
     *
     * @param out the logging consumer
     */
    static ClassPath create(Consumer<String> out) {
        return new ClassPathImpl(requireNonNull(out));
    }

    /**
     * Adds a library to the sources of this classpath.
     * The implementation is free to accept or not accept paths of any kind.
     * Libraries are used when querying class information.
     *
     * @param path the path object
     */
    void addLibrary(Path path);

    /**
     * Adds class bytes for a class to this classpath.
     * This may be an optional operation depending on the implementation.
     *
     * @param cls the fully resolved classname, see {@link Type#getInternalName()}
     * @param data the class bytes
     */
    void addClass(String cls, byte[] data);

    /**
     * Queries the class information from this class path.
     * An empty optional will be returned if the class cannot be found
     * in the registered class bytes or a registered library.
     *
     * @param cls the fully resolved classname, see {@link Type#getInternalName()}
     * @return the optional class information
     */
    Optional<? extends IClassInfo> getClass(String cls);

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
