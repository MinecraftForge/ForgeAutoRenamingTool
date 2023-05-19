package net.minecraftforge.fart.internal;

import net.minecraftforge.fart.api.ClassProvider;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;

public class ClassLoaderClassProvider implements ClassProvider {
    private final ClassLoader classLoader;

    public ClassLoaderClassProvider(@Nullable ClassLoader classLoader) {
        this.classLoader = classLoader == null ? this.getClass().getClassLoader() : classLoader;
    }

    @Override
    public Optional<? extends IClassInfo> getClass(String name) {
        try {
            Class<?> cls = Class.forName(name.replace('/', '.'), false, this.classLoader);
            return Optional.of(new ClassProviderImpl.ClassInfo(cls));
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws IOException {}
}
