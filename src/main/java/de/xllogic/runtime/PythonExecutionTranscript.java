package de.xllogic.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class PythonExecutionTranscript {
    private static final int MAX_RECORDED_STDOUT_LINES = 256;
    private static final int MAX_RECORDED_STDERR_LINES = 256;
    private static final int MAX_RECORDED_OUTPUT_ENTRIES = 256;

    private final RecordingOutputStream stdoutStream;
    private final RecordingOutputStream stderrStream;
    private final List<String> stdoutLines = new ArrayList<>();
    private final List<String> stderrLines = new ArrayList<>();
    private final List<ComputerOutputEntry> outputEntries = new ArrayList<>();

    PythonExecutionTranscript(final long maxStdoutBytes, final long maxStderrBytes) {
        this.stdoutStream = new RecordingOutputStream("stdout", maxStdoutBytes, this::recordStdoutLine);
        this.stderrStream = new RecordingOutputStream("stderr", maxStderrBytes, this::recordStderrLine);
    }

    OutputStream stdoutStream() {
        return this.stdoutStream;
    }

    OutputStream stderrStream() {
        return this.stderrStream;
    }

    void flush() {
        try {
            this.stdoutStream.flush();
            this.stderrStream.flush();
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to flush Python execution transcript.", exception);
        }
    }

    synchronized void recordStructuredOutput(final ComputerOutputEntry outputEntry) {
        if (outputEntry != null) {
            this.outputEntries.add(outputEntry);
            trimOldest(this.outputEntries, MAX_RECORDED_OUTPUT_ENTRIES);
        }
    }

    synchronized List<String> stdoutLines() {
        return List.copyOf(this.stdoutLines);
    }

    synchronized List<String> stderrLines() {
        return List.copyOf(this.stderrLines);
    }

    synchronized List<ComputerOutputEntry> outputEntries() {
        return List.copyOf(this.outputEntries);
    }

    private synchronized void recordStdoutLine(final String line) {
        final String safeLine = line == null ? "" : line;
        this.stdoutLines.add(safeLine);
        trimOldest(this.stdoutLines, MAX_RECORDED_STDOUT_LINES);
        this.outputEntries.add(ComputerOutputEntry.stdout(safeLine));
        trimOldest(this.outputEntries, MAX_RECORDED_OUTPUT_ENTRIES);
    }

    private synchronized void recordStderrLine(final String line) {
        final String safeLine = line == null ? "" : line;
        this.stderrLines.add(safeLine);
        trimOldest(this.stderrLines, MAX_RECORDED_STDERR_LINES);
        this.outputEntries.add(ComputerOutputEntry.stderr(safeLine));
        trimOldest(this.outputEntries, MAX_RECORDED_OUTPUT_ENTRIES);
    }

    private static <T> void trimOldest(final List<T> entries, final int maxEntries) {
        while (entries.size() > maxEntries) {
            entries.remove(0);
        }
    }

    private static final class RecordingOutputStream extends OutputStream {
        private final String channelName;
        private final long maxBytes;
        private final Consumer<String> lineConsumer;
        private final ByteArrayOutputStream currentLine = new ByteArrayOutputStream();
        private long bytesWritten;

        private RecordingOutputStream(final String channelName, final long maxBytes, final Consumer<String> lineConsumer) {
            this.channelName = channelName;
            this.maxBytes = Math.max(0L, maxBytes);
            this.lineConsumer = lineConsumer;
        }

        @Override
        public void write(final int value) {
            if (value == '\r') {
                return;
            }
            this.ensureRemainingCapacity(1L);
            if (value == '\n') {
                this.emitLine(true);
                return;
            }
            this.currentLine.write(value);
        }

        @Override
        public void write(final byte[] buffer, final int offset, final int length) {
            for (int index = 0; index < length; index++) {
                this.write(buffer[offset + index]);
            }
        }

        @Override
        public void flush() throws IOException {
            this.emitLine(false);
        }

        @Override
        public void close() throws IOException {
            this.emitLine(false);
        }

        private void emitLine(final boolean allowEmptyLine) {
            if (!allowEmptyLine && this.currentLine.size() == 0) {
                return;
            }

            final String line = this.currentLine.toString(StandardCharsets.UTF_8);
            this.currentLine.reset();
            this.lineConsumer.accept(line);
        }

        private void ensureRemainingCapacity(final long additionalBytes) {
            if (this.maxBytes <= 0L) {
                return;
            }

            if (this.bytesWritten + additionalBytes > this.maxBytes) {
                throw new OutputLimitExceededException(this.channelName, this.maxBytes);
            }
            this.bytesWritten += additionalBytes;
        }
    }
}