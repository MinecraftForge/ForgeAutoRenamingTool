package net.minecraftforge.renamer.gradle;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarFile;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import net.minecraftforge.gradleutils.shared.EnhancedTask;
import net.minecraftforge.gradleutils.shared.SharedUtil;

public abstract class RenamerTask extends DefaultTask implements EnhancedTask<RenamerProblems> {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = Logging.getLogger(RenamerExtension.class);

    private final RenamerProblems problems = this.getObjects().newInstance(this.problemsType());

    public @InputFile abstract RegularFileProperty getInput();
    public @InputFiles @Classpath abstract ConfigurableFileCollection getMap();
    public @InputFiles @Classpath abstract ConfigurableFileCollection getToolClasspath();
    public @InputFiles @Classpath abstract ConfigurableFileCollection getClasspath();
    public @Input abstract ListProperty<String> getArgs();
    public @Nested abstract Property<JavaLauncher> getJavaLauncher();
    public @OutputFile abstract RegularFileProperty getOutput();

    public @Internal abstract RegularFileProperty getLogFile();
    public @Internal abstract DirectoryProperty getWorkingDir();

    protected abstract @Inject ProviderFactory getProviders();
    protected abstract @Inject JavaToolchainService getJavaToolchains();
    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ExecOperations getExecOperations();

    @Override
    public Class<? extends EnhancedPlugin<? super Project>> pluginType() {
        return RenamerPlugin.class;
    }

    @Override
    public Class<RenamerProblems> problemsType() {
        return RenamerProblems.class;
    }

    @TaskAction
    protected ExecResult run() throws IOException {
        var logger = getLogger();

        var stdOutLevel = LogLevel.LIFECYCLE;
        var stdErrLevel = LogLevel.ERROR;

        var javaLauncher = getJavaLauncher().get();

        var workingDirectory = this.getWorkingDir().map(problems.ensureFileLocation()).get().getAsFile();

        try (var log = new PrintWriter(new FileWriter(this.getLogFile().getAsFile().get()), true)) {
            return getExecOperations().javaexec(spec -> {
                spec.setIgnoreExitValue(true);
                spec.setWorkingDir(workingDirectory);
                spec.setClasspath(this.getToolClasspath());
                spec.getMainClass().set(this.getMainClass());
                spec.setExecutable(javaLauncher.getExecutablePath().getAsFile().getAbsolutePath());
                spec.setArgs(this.fillArgs());
                //spec.setJvmArgs(jvmArgs);
                //spec.setEnvironment(this.environment);
                //spec.setSystemProperties(this.systemProperties);

                spec.setStandardOutput(SharedUtil.toLog(
                    line -> {
                        logger.log(stdOutLevel, line);
                        log.println(line);
                    }
                ));
                spec.setErrorOutput(SharedUtil.toLog(
                    line -> {
                        logger.log(stdErrLevel, line);
                        log.println(line);
                    }
                ));

                log.print("Java Launcher: ");
                log.println(spec.getExecutable());
                log.print("Working directory: ");
                log.println(spec.getWorkingDir().getAbsolutePath());
                log.print("Main class: ");
                log.println(spec.getMainClass().get());
                log.println("Arguments:");
                for (var s : spec.getArgs()) {
                    log.print("  ");
                    log.println(s);
                }
                log.println("JVM Arguments:");
                for (var s : spec.getAllJvmArgs()) {
                    log.print("  ");
                    log.println(s);
                }
                log.println("Classpath:");
                for (var f : getClasspath()) {
                    log.print("  ");
                    log.println(f.getAbsolutePath());
                }
                log.println("====================================");
            });
        }

    }

    private File getMapFile() {
        try {
            return this.getMap().getSingleFile();
        } catch (IllegalStateException exception) {
            problems.reportMultipleMapFiles(this);
            throw exception;
        }
    }

    private static String path(RegularFileProperty prop) {
        return prop.getAsFile().get().getAbsolutePath();
    }

    private List<String> fillArgs() {
        record Value(String value, List<String> values) {}
        var map = new HashMap<String, Value>();
        map.put("input", new Value(path(this.getInput()), null));
        map.put("output", new Value(path(this.getOutput()), null));
        map.put("map", new Value(this.getMapFile().getAbsolutePath(), null));

        var libs = new ArrayList<String>();
        this.getClasspath().forEach(file -> libs.add(file.getAbsolutePath()));
        map.put("library", new Value(null, libs));

        var args = new ArrayList<String>();
        for (var arg : this.getArgs().get()) {
            var start = arg.indexOf('{');
            if (start == -1) {
                args.add(arg);
                continue;
            }

            var end = arg.indexOf('}', start);
            if (end == -1)
                throw new IllegalArgumentException("Unmatched variable replacement in " + this.getArgs().get());

            var key = arg.substring(start + 1, end);
            var value = map.get(key);
            if (value == null)
                throw new IllegalArgumentException("Unknown variable replacement: " + key);

            var prefix = arg.substring(0, start);
            var suffix = arg.substring(end + 1);

            if (value.value != null)
                args.add(prefix + value.value + suffix);
            else if (value.values != null) {
                for (var data : value.values)
                    args.add(prefix + data + suffix);
            } else
                throw new IllegalStateException("Replacement " + key + " did not have a value");
        }
        return args;
    }

    private String getMainClass() {
        File tool = null;
        try {
            tool = this.getToolClasspath().getSingleFile();
        } catch (IllegalStateException exception) {
            problems.reportNoMainClass(this);
            throw exception;
        }

        try (var jar = new JarFile(tool)) {
            var manifest = jar.getManifest();
            if (manifest == null) {
                problems.reportNoMainClass(this);
                throw new IllegalStateException("Tool jar does not have manifest: " + tool.getAbsolutePath());
            }
            var mainClass = manifest.getMainAttributes().getValue("Main-Class");
            if (mainClass == null) {
                problems.reportNoMainClass(this);
                throw new IllegalStateException("Tool jar does not have Main-Class entry in its Manifest: " + tool.getAbsolutePath());
            }
            return mainClass;
        } catch (IOException e) {
            problems.reportNoMainClass(this);
            throw new IllegalStateException("Tool jar does not have Main-Class entry in its Manifest: " + tool.getAbsolutePath(), e);
        }
    }
}
