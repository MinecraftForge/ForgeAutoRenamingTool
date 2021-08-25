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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.fart.api.Inheritance;
import net.minecraftforge.fart.api.Renamer;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.fart.api.Transformer.ClassEntry;
import net.minecraftforge.fart.api.Transformer.Entry;
import net.minecraftforge.fart.api.Transformer.ManifestEntry;
import net.minecraftforge.fart.api.Transformer.ResourceEntry;

class RenamerImpl implements Renamer {
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    private final File input;
    private final File output;
    private final List<File> libraries;
    private final List<Transformer> transformers;
    private final Inheritance inh;

    RenamerImpl(File input, File output, List<File> libraries, List<Transformer> transformers, Inheritance inh) {
        this.input = input;
        this.output = output;
        this.libraries = libraries;
        this.transformers = transformers;
        this.inh = inh;
    }

    @Override
    public void run() {
        log("Adding Libraries to Inheritance");
        libraries.forEach(inh::addLibrary);

        if (!input.exists())
            throw new IllegalArgumentException("Input file not found: " + input.getAbsolutePath());

        log("Reading Input: " + input.getAbsolutePath());
        // Read everything from the input jar!
        List<Entry> oldEntries = new ArrayList<>();
        try (ZipFile in = new ZipFile(input)) {
            Util.forZip(in, e -> {
                if (e.isDirectory())
                    return;
                String name = e.getName();
                byte[] data = Util.toByteArray(in.getInputStream(e));

                if (name.endsWith(".class"))
                    oldEntries.add(ClassEntry.create(name, e.getTime(), data));
                else if (name.equals(MANIFEST_NAME))
                    oldEntries.add(ManifestEntry.create(e.getTime(), data));
                else
                    oldEntries.add(ResourceEntry.create(name, e.getTime(), data));
            });
        } catch (IOException e) {
            throw new RuntimeException("Could not parse input: " + input.getAbsolutePath(), e);
        }

        AsyncHelper async = new AsyncHelper();

        // Gather original file Hashes, so that we can detect changes and update the manifest if necessary
        log("Gathering original hashes");
        Map<String, String> oldHashes = async.invokeAll(oldEntries,
            e -> new Pair<>(e.getName(), HashFunction.SHA256.hash(e.getData()))
        ).stream().collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        List<ClassEntry> ourClasses = oldEntries.stream()
            .filter(e -> e instanceof ClassEntry && !e.getName().startsWith("META-INF/"))
            .map(ClassEntry.class::cast)
            .collect(Collectors.toList());

        // Add the original classes to the inheritance map, TODO: Multi-Release somehow?
        log("Adding input to inheritence map");
        async.consumeAll(ourClasses, c ->
            inh.addClass(c.getName().substring(0, c.getName().length() - 6), c.getData())
        );

        // Process everything
        log("Processing entries");
        List<Entry> newEntries = async.invokeAll(oldEntries, this::processEntry);

        log("Adding extras");
        transformers.stream().forEach(t -> newEntries.addAll(t.getExtras()));

        Set<String> seen = new HashSet<>();
        String dupes = newEntries.stream().map(Entry::getName)
            .filter(n -> !seen.add(n))
            .sorted()
            .collect(Collectors.joining(", "));
        if (!dupes.isEmpty())
            throw new IllegalStateException("Duplicate entries detected: " + dupes);

        log("Collecting new hashes");
        Map<String, String> newHashes = async.invokeAll(newEntries,
            e -> new Pair<>(e.getName(), HashFunction.SHA256.hash(e.getData()))
        ).stream().collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // We care about stable output, so sort, and single thread write.
        log("Sorting");
        Collections.sort(newEntries, this::compare);

        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        seen.clear();
        log("Writing Output: " + output.getAbsolutePath());
        try (FileOutputStream fos = new FileOutputStream(output);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (Entry e : newEntries) {
                String name = e.getName();
                int idx = name.lastIndexOf('/');
                if (idx != -1)
                    addDirectory(zos, seen, name.substring(0, idx));

                log("  " + name);
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(e.getTime());
                zos.putNextEntry(entry);
                zos.write(e.getData());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Tho Directory entries are not strictly necessary, we add them because some bad implementations of Zip extractors
    // attempt to extract files without making sure the parents exist.
    private void addDirectory(ZipOutputStream zos, Set<String> seen, String path) throws IOException {
        if (!seen.add(path))
            return;

        int idx = path.lastIndexOf('/');
        if (idx != -1)
            addDirectory(zos, seen, path.substring(0, idx));

        log("  " + path + '/');
        ZipEntry dir = new ZipEntry(path + '/');
        dir.setTime(Entry.STABLE_TIMESTAMP);
        zos.putNextEntry(dir);
        zos.closeEntry();
    }

    private void log(String line) {
        System.out.println(line);
    }

    private Entry processEntry(final Entry start) {
        Entry entry = start;
        for (Transformer transformer : RenamerImpl.this.transformers) {
            entry = entry.process(transformer);
            if (entry == null)
                return null;
        }
        return entry;
    }

    private int compare(Entry o1, Entry o2) {
        // In order for JarInputStream to work, MANIFEST has to be the first entry, so make it first!
        if (MANIFEST_NAME.equals(o1.getName()))
            return MANIFEST_NAME.equals(o2.getName()) ? 0 : -1;
        if (MANIFEST_NAME.equals(o2.getName()))
            return MANIFEST_NAME.equals(o1.getName()) ? 0 :  1;
        return o1.getName().compareTo(o2.getName());
    }
}
