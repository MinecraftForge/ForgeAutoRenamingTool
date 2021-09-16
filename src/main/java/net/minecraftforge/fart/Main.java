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

package net.minecraftforge.fart;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;
import net.minecraftforge.fart.api.Renamer;

public class Main {
    static final int MAX_ASM_VERSION = Opcodes.ASM9;
    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO  = parser.accepts("input",  "Input jar file").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output", "Output jar file, if unspecifed, overwrites input").withRequiredArg().ofType(File.class);
        OptionSpec<File> mapO    = parser.acceptsAll(Arrays.asList("map", "names"),    "Mapping file to apply").withRequiredArg().ofType(File.class);
        OptionSpec<File> logO    = parser.accepts("log",    "File to log data to, optional, defaults to System.out").withRequiredArg().ofType(File.class);
        OptionSpec<File> libO    = parser.acceptsAll(Arrays.asList("lib", "e"), "Additional library to use for inheritence").withRequiredArg().ofType(File.class);
        OptionSpec<Void> fixAnnO = parser.accepts("ann-fix", "Fixes misaligned parameter annotations caused by Proguard.");
        OptionSpec<Void> fixRecordsO = parser.accepts("record-fix", "Fixes record component data stripped by Proguard.");
        OptionSpec<IdentifierFixer.Config> fixIdsO = parser.accepts("ids-fix", "Fixes local variables that are not valid java identifiers.").withOptionalArg().withValuesConvertedBy(new IDConverter()).defaultsTo(IdentifierFixer.Config.ALL);
        OptionSpec<SourceFixer.Config> fixSrcO = parser.accepts("src-fix", "Fixes the 'SourceFile' attribute of classes.").withOptionalArg().withValuesConvertedBy(new SrcConverter()).defaultsTo(SourceFixer.Config.JAVA);
        OptionSpec<Integer> threadsO = parser.accepts("threads", "Number of threads to use, defaults to processor count.").withRequiredArg().ofType(Integer.class).defaultsTo(Runtime.getRuntime().availableProcessors());
        OptionSpec<File> ffLinesO = parser.accepts("ff-line-numbers", "Applies line number corrections from Fernflower.").withRequiredArg().ofType(File.class);
        OptionSet options = parser.parse(expandArgs(args));

        if (options.has(logO)) {
            PrintStream out = System.out;
            PrintStream log = new PrintStream(new FileOutputStream(options.valueOf(logO)));
            hookStdOut(ln -> {
                out.println(ln);
                log.println(ln);
            });
        } else {
            hookStdOut(System.out::println);
        }

        log("Forge Auto Renaming Tool v" + getVersion());
        Renamer.Builder builder = Renamer.builder();

        // Move this up top so that the log lines are above the rest of the config as they can be spammy.
        // Its useful information but we care more about the specific configs.
        if (options.has(libO)) {
            for (File lib : options.valuesOf(libO)) {
                log("lib: " + lib.getAbsolutePath());
                builder.lib(lib);
            }
        }

        log("log: " + (options.has(logO) ? options.valueOf(logO).getAbsolutePath() : "null"));

        File inputF = options.valueOf(inputO);
        log("input: " + inputF.getAbsolutePath());
        builder.input(inputF);

        File outputF = options.has(outputO) ? options.valueOf(outputO) : inputF;
        log("output: " + outputF.getAbsolutePath());
        builder.output(outputF);

        log("threads: " + options.valueOf(threadsO));
        builder.threads(options.valueOf(threadsO));

        // Map is optional so that we can run other fixes without renaming.
        // This does mean that it's not strictly a 'renaming' tool but screw it I like the name.
        if (options.has(mapO)) {
            File mapF = options.valueOf(mapO);
            log("Names: " + mapF.getAbsolutePath());
            builder.map(mapF);
        } else {
            log("Names: null");
        }

        if (options.has(fixAnnO)) {
            log("Fix Annotations: true");
            builder.add(new ParameterAnnotationFixer());
        } else {
            log("Fix Annotations: false");
        }

        if (options.has(fixRecordsO)) {
            log("Fix Records: true");
            builder.add(new RecordFixer());
        } else {
            log("Fix Records: false");
        }

        if (options.has(fixIdsO)) {
            log("Fix Identifiers: " + options.valueOf(fixIdsO));
            builder.add(new IdentifierFixer(options.valueOf(fixIdsO)));
        } else {
            log("Fix Identifiers: false");
        }

        if (options.has(fixSrcO)) {
            log("Fix SourceFile: " + options.valueOf(fixSrcO));
            builder.add(new SourceFixer(options.valueOf(fixSrcO)));
        } else {
            log("Fix SourceFile: false");
        }

        if (options.has(ffLinesO)) {
            File lines = options.valueOf(ffLinesO);
            log("Fix Line Numbers: " + lines.getAbsolutePath());
            builder.add(new FFLineFixer(lines));
        } else {
            log("Fix Line Numbers: false");
        }

        Renamer renamer = builder.build();
        renamer.run();
    }

    private static void log(String line) {
        System.out.println(line);
    }

    private static String[] expandArgs(String[] args) throws IOException {
        List<String> ret = new ArrayList<>();
        for (int x = 0; x < args.length; x++) {
            if (args[x].equals("--cfg")) {
                if (x + 1 == args.length)
                    throw new IllegalArgumentException("No value specified for '--cfg'");

                Files.lines(Paths.get(args[++x])).forEach(ret::add);
            } else if (args[x].startsWith("--cfg=")) {
                Files.lines(Paths.get(args[x].substring(6))).forEach(ret::add);
            } else {
                ret.add(args[x]);
            }
        }

        return ret.toArray(new String[ret.size()]);
    }

    private static String getVersion() {
        final String ver = Main.class.getPackage().getImplementationVersion();
        return ver == null ? "UNKNOWN" : ver;
    }

    static void hookStdOut(final Consumer<String> consumer) {
        final OutputStream monitorStream = new OutputStream() {
            private byte[] buf = new byte[128];
            private int index = 0;

            private void ensure(int len) {
                if (buf.length <= len) {
                    byte[] old = buf;
                    int max = buf.length << 1;
                    while (max > 0 && max < len) {
                        max += 1024;
                    }
                    if (max < 0)
                        throw new OutOfMemoryError();
                    buf = Arrays.copyOf(old, max);
                }
            }

            private void send() {
                if (index == 0)
                    return; // TODO: Detect and support multiple empty lines?
                String line = new String(buf, 0, index);
                buf = new byte[128];
                index = 0;
                consumer.accept(line);
            }

            @Override
            public synchronized void write(int b) {
                if (b == '\r' || b == '\n') {
                    send();
                } else {
                    ensure(index + 1);
                    buf[index++] = (byte)b;
                }
            }

            @Override
            public synchronized void write(byte b[], int off, int len) {
                if (off < 0 || len < 0 || off > b.length || (off + len) >= b.length)
                    throw new IndexOutOfBoundsException();

                while (len > 0) {
                    int x = 0;
                    for (; x < len; x++) {
                        byte i = b[off + x];
                        if (i == '\r' || i == '\n')
                            break;
                    }
                    ensure(index + x);
                    System.arraycopy(b, off, buf, index, x);
                    index += x;

                    if (x != len) {
                        send();
                        x++; //Skip this char
                        len -= x;
                        off += x;
                        if (b[off - 1] == '\r' && b[off] == '\n') {
                            len--;
                            off++;
                        }
                    } else {
                        off += len;
                        len = 0;
                    }
                }
            }
        };

        System.setOut(new PrintStream(monitorStream));
        System.setErr(new PrintStream(monitorStream));
    }

    private static class IDConverter implements ValueConverter<IdentifierFixer.Config> {
        @Override
        public IdentifierFixer.Config convert(String value) {
            return IdentifierFixer.Config.valueOf(value.toUpperCase(Locale.ENGLISH));
        }

        @Override
        public Class<? extends IdentifierFixer.Config> valueType() {
            return IdentifierFixer.Config.class;
        }

        @Override
        public String valuePattern() {
            return Arrays.stream(IdentifierFixer.Config.values()).map(Enum::name).collect(Collectors.joining("|"));
        }
    }

    private static class SrcConverter implements ValueConverter<SourceFixer.Config> {
        @Override
        public SourceFixer.Config convert(String value) {
            return SourceFixer.Config.valueOf(value.toUpperCase(Locale.ENGLISH));
        }

        @Override
        public Class<? extends SourceFixer.Config> valueType() {
            return SourceFixer.Config.class;
        }

        @Override
        public String valuePattern() {
            return Arrays.stream(SourceFixer.Config.values()).map(Enum::name).collect(Collectors.joining("|"));
        }
    }
}
