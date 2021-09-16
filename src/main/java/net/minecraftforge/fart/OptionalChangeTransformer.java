package net.minecraftforge.fart;

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
            this(Main.MAX_ASM_VERSION, parent);
        }

        protected ClassFixer(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        public boolean madeChange() {
            return this.madeChange;
        }
    }
}
