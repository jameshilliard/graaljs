/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.truffle.js.snapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.nodes.NodeFactory;
import com.oracle.truffle.js.nodes.ScriptNode;
import com.oracle.truffle.js.parser.InternalTranslationProvider;
import com.oracle.truffle.js.parser.JSEngine;
import com.oracle.truffle.js.parser.JavaScriptTranslator;
import com.oracle.truffle.js.runtime.AbstractJavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSTruffleOptions;

public class SnapshotTool {
    static {
        System.setProperty("truffle.js.Snapshots", "false");
        System.setProperty("truffle.js.LazyTranslation", "false");
    }

    private final TimeStats timeStats = new TimeStats();
    private JSContext context;

    public SnapshotTool() {
    }

    static JSContext createDefaultContext() {
        return JSEngine.createJSContext();
    }

    private void snapshotInternalFileTo(String fileName, OutputStream outputStream, boolean binary) {
        try (TimerCloseable timer = timeStats.file(fileName)) {
            Recording.logv("recording snapshot of %s", fileName);
            final Recording rec = new Recording();
            ScriptNode program = InternalTranslationProvider.interceptTranslation(getContext(), fileName, fac -> RecordingProxy.createRecordingNodeFactory(rec, fac));
            rec.finish(program.getRootNode());
            rec.saveToStream(fileName, outputStream, binary);
        }
    }

    private void snapshotInternalFile(String fileName, String destDir, boolean binary) {
        String qualifiedClassName = InternalTranslationProvider.classNameFromFileName(fileName);
        File outputFile = new File(destDir, qualifiedClassName.replace('.', '/') + (binary ? ".bin" : ".java"));
        outputFile.getParentFile().mkdirs();
        try (FileOutputStream outs = new FileOutputStream(outputFile)) {
            snapshotInternalFileTo(fileName, outs, binary);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        assert !JSTruffleOptions.Snapshots;
        assert !JSTruffleOptions.LazyTranslation;

        boolean binary = true;
        boolean internal = true;
        String outDir = null;
        String inDir = null;
        List<String> srcFiles = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                if (arg.equals("--internal")) {
                    internal = true;
                } else if (arg.equals("--java")) {
                    binary = false;
                } else if (arg.equals("--binary")) {
                    binary = true;
                } else if (arg.startsWith("--file=")) {
                    internal = false;
                    srcFiles.add(arg.substring(arg.indexOf('=') + 1));
                } else if (arg.startsWith("--outdir=")) {
                    outDir = requireDirectory(arg.substring(arg.indexOf('=') + 1));
                } else if (arg.startsWith("--indir=")) {
                    inDir = requireDirectory(arg.substring(arg.indexOf('=') + 1));
                }
            }
        }

        SnapshotTool snapshotTool = new SnapshotTool();
        if (internal && outDir != null) {
            snapshotTool.snapshotInternalFiles(outDir, binary);
        } else if (!internal && !srcFiles.isEmpty() && outDir != null) {
            for (String srcFile : srcFiles) {
                File sourceFile = inDir == null ? new File(srcFile) : Paths.get(inDir, srcFile).toFile();
                File outputFile = Paths.get(outDir, srcFile + (binary ? ".bin" : ".java")).toFile();
                if (!sourceFile.isFile()) {
                    throw new IllegalArgumentException("Not a file: " + sourceFile);
                }
                snapshotTool.snapshotScriptFileTo(srcFile, sourceFile, outputFile, binary);
            }
            snapshotTool.timeStats.print();
        } else {
            System.out.println("Usage: [--java|--binary] --internal --outdir=DIR");
            System.out.println("Usage: [--java|--binary] --outdir=DIR [--indir=DIR] --file=FILE [--file=FILE ...]");
        }
    }

    private static String requireDirectory(String dir) {
        if (dir != null && !new File(dir).isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + dir);
        }
        return dir;
    }

    private void snapshotInternalFiles(String destDir, boolean binary) {
        InternalTranslationProvider.forEachInternalSourceFile(fileName -> snapshotInternalFile(fileName, destDir, binary));
        timeStats.print();
    }

    private void snapshotScriptFileTo(String fileName, File sourceFile, File outputFile, boolean binary) throws IOException {
        Recording.logv("recording snapshot of %s", fileName);
        Source source = Source.newBuilder(sourceFile).name(fileName).mimeType(AbstractJavaScriptLanguage.APPLICATION_MIME_TYPE).build();
        final Recording rec;
        try (TimerCloseable timer = timeStats.file(fileName)) {
            rec = new Recording();
            ScriptNode program = JavaScriptTranslator.translateScript(RecordingProxy.createRecordingNodeFactory(rec, NodeFactory.getInstance(getContext())), getContext(), source, false);
            rec.finish(program.getRootNode());
            outputFile.getParentFile().mkdirs();
            try (FileOutputStream outs = new FileOutputStream(outputFile)) {
                rec.saveToStream(fileName, outs, binary);
            }
        } catch (RuntimeException e) {
            throw new RuntimeException(fileName, e);
        }
    }

    private JSContext getContext() {
        if (context == null) {
            return context = createDefaultContext();
        }
        return context;
    }

    private interface TimerCloseable extends AutoCloseable {
        @Override
        void close();
    }

    private static class TimeStats {
        private final List<Map.Entry<String, Long>> entries = new ArrayList<>();

        public TimerCloseable file(String fileName) {
            long startTime = System.nanoTime();
            return () -> {
                long endTime = System.nanoTime();
                entries.add(new AbstractMap.SimpleImmutableEntry<>(fileName, endTime - startTime));
            };
        }

        public void print() {
            if (entries.isEmpty()) {
                return;
            }
            long total = 0;
            for (Map.Entry<String, Long> entry : entries) {
                System.out.printf("%s: %.02f ms\n", entry.getKey(), entry.getValue() / 1e6);
                total += entry.getValue();
            }
            System.out.printf("Total: %.02f ms\n", total / 1e6);
        }
    }
}