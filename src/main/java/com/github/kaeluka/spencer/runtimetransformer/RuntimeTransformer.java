package com.github.kaeluka.spencer.runtimetransformer;

import com.github.kaeluka.instrumentation.Instrument;
import com.github.kaeluka.instrumentation.Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class RuntimeTransformer {
    public static void main(String[] args) throws IOException {
        if (args.length >= 2) {
            System.err.println("usage: java "
                    +RuntimeTransformer.class.getName()
                    +" [targetDir]");
            System.exit(1);
        }
        final File targetDir = new File(args.length == 1 ? args[0] : "instrumented_java_rt");

        final String objectClassFile =
                RuntimeTransformer
                        .class
                        .getClassLoader()
                        .getResource("java/lang/Object.class").toString();
        final String rtJarFile = objectClassFile.toString().split("!")[0].replace("jar:file:", "");

        System.out.println("transforming classes in "+rtJarFile);
        System.out.println("writing transformed files to "+targetDir.getAbsolutePath());
        System.out.println("this could take several minutes...");

        System.out.print("deleting old files...");
        FileUtils.deleteDirectory(targetDir);
        System.out.println(" done");
        targetDir.mkdirs();

        final long startTime = System.nanoTime();
        final URL url = new URL("file:"+rtJarFile);
        transformAndDumpClassesToDir(url, targetDir.getAbsolutePath());
        final long diff = ((System.nanoTime()-startTime)/1000000000);
        System.out.println("transformation of "+url.getFile()+" took "+diff+"sec.");
        for (String err : Instrument.getErrors()) {
            System.err.println("Could not instrument: "+err);
        }

        final String name = "java/lang/AbstractStringBuilder";
        System.out.println(name+" is instrumented: "+!Util.isClassNameBlacklisted(name));
    }

    private static void transformAndDumpClassesToDir(final URL url, String dir) {
        try {
            if (url.getFile().endsWith(".jar")) {
                JarFile jarFile = new JarFile(url.getPath());
                final ArrayList<JarEntry> entries = Collections.list(jarFile.entries());
                int cnt = 0, skipped = 0, instrumented = 0;

                entries.stream().parallel()
                        .filter(e -> !Util.isClassNameBlacklisted(e.getName()))
                        .forEach(e -> {
                            try {
                                final File originalFile = new File(dir+"/input/"+e.getName());
                                if (e.isDirectory()) {
                                    originalFile.mkdirs();
                                    new File(dir+"/output/"+e.getName()).mkdirs();
                                } else {
                                    originalFile.getParentFile().mkdirs();
                                    originalFile.createNewFile();

                                    FileOutputStream originalDestStream = new FileOutputStream(originalFile);
                                    final InputStream sourceStream = jarFile.getInputStream(e);
                                    final byte[] originalByteCode = IOUtils.toByteArray(sourceStream);
                                    originalDestStream.write(originalByteCode);

                                    if (e.getName().endsWith(".class")) {
                                        final byte[] transformedByteCode = Instrument.transform(originalByteCode);
                                        if (Arrays.equals(originalByteCode, transformedByteCode)) {
                                            // FIXME: most (all?) of these might be interfaces. They shouldn't count as skipped.
                                            if (Instrument.isInterface(originalByteCode)) {

                                                System.out.println("PHEW, "+e.getName()+" is an interface!");
//											instrumented++;
                                            } else {
                                                System.out.println("bytecode for "+e.getName()+" not changed");
//											skipped++;
                                            }
                                        } else {
                                            final File transformedFile = new File(dir+"/output/"+e.getName());
                                            transformedFile.getParentFile().mkdirs();
                                            transformedFile.createNewFile();
                                            FileOutputStream transformedDestStream = new FileOutputStream(transformedFile);
                                            transformedDestStream.write(transformedByteCode);
                                            transformedDestStream.close();
//										instrumented++;
                                        }
                                    } else {
                                        // not a class file -- keeping it as-is!
                                    }
                                    originalDestStream.close();
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        });
                jarFile.close();
//				System.out.println("When instrumenting java core runtime classes:\n"
//						+ "skipped "+skipped+" classes, instrumented "+instrumented+" classes ("+(instrumented*100/(skipped+instrumented))+"%)");
                System.out.println("When instrumenting java core runtime classes:\n"
                        + "skipped "+skipped);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
