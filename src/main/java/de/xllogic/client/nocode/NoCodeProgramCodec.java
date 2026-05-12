package de.xllogic.client.nocode;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

public final class NoCodeProgramCodec {
    private static final Gson GSON = new Gson();
    private static final String METADATA_PREFIX = "# xllogic:no_code=";
    private static final int METADATA_SCAN_LINES = 8;
    private static final int VERSION = 3;

    private NoCodeProgramCodec() {
    }

    public static String metadataComment(final NoCodeProgram program) {
        return METADATA_PREFIX + GSON.toJson(toStoredProgram(program));
    }

    public static DecodedProgram decode(final String script) {
        final String safeScript = script == null ? "" : normalizeLineSeparators(script);
        final String[] lines = safeScript.split("\n", -1);
        for (int index = 0; index < Math.min(lines.length, METADATA_SCAN_LINES); index++) {
            final String line = lines[index];
            if (!line.startsWith(METADATA_PREFIX)) {
                continue;
            }
            try {
                final StoredProgram storedProgram = GSON.fromJson(line.substring(METADATA_PREFIX.length()), StoredProgram.class);
                final NoCodeProgram program = fromStoredProgram(storedProgram);
                final boolean matchesCurrentScript = safeScript.equals(normalizeLineSeparators(NoCodeScriptGenerator.generate(program)));
                return new DecodedProgram(program, true, matchesCurrentScript, false);
            } catch (RuntimeException exception) {
                return new DecodedProgram(new NoCodeProgram(), true, false, true);
            }
        }
        return new DecodedProgram(new NoCodeProgram(), false, false, false);
    }

    private static StoredProgram toStoredProgram(final NoCodeProgram program) {
        final NoCodeProgram safeProgram = program == null ? new NoCodeProgram() : program;
        final ArrayList<StoredBlock> blocks = new ArrayList<>();
        for (final NoCodeBlock block : safeProgram.blocks()) {
            if (block == null) {
                continue;
            }
            blocks.add(new StoredBlock(block.kind().name(), block.text(), block.deviceApiName(), block.sideName(), block.targetDeviceApiName(), block.targetSideName(), block.level()));
        }
        return new StoredProgram(VERSION, safeProgram.repeat(), safeProgram.repeatTicks(), blocks);
    }

    private static NoCodeProgram fromStoredProgram(final StoredProgram storedProgram) {
        final NoCodeProgram program = new NoCodeProgram();
        if (storedProgram == null) {
            return program;
        }
        program.setRepeat(storedProgram.repeat);
        program.setRepeatTicks(storedProgram.repeatTicks);
        if (storedProgram.blocks != null) {
            for (final StoredBlock storedBlock : storedProgram.blocks) {
                if (storedBlock == null) {
                    continue;
                }
                NoCodeBlockKind kind;
                try {
                    kind = NoCodeBlockKind.valueOf(storedBlock.kind);
                } catch (IllegalArgumentException | NullPointerException exception) {
                    kind = NoCodeBlockKind.PRINT_TEXT;
                }
                program.blocks().add(new NoCodeBlock(kind, storedBlock.text, storedBlock.deviceApiName, storedBlock.sideName, storedBlock.targetDeviceApiName, storedBlock.targetSideName, storedBlock.level));
            }
        }
        return program;
    }

    private static String normalizeLineSeparators(final String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    public record DecodedProgram(NoCodeProgram program, boolean foundMetadata, boolean matchesGeneratedScript, boolean parseError) {
    }

    private static final class StoredProgram {
        private int version;
        private boolean repeat;
        private int repeatTicks;
        private List<StoredBlock> blocks;

        private StoredProgram(final int version, final boolean repeat, final int repeatTicks, final List<StoredBlock> blocks) {
            this.version = version;
            this.repeat = repeat;
            this.repeatTicks = repeatTicks;
            this.blocks = blocks;
        }
    }

    private static final class StoredBlock {
        private String kind;
        private String text;
        private String deviceApiName;
        private String sideName;
        private String targetDeviceApiName;
        private String targetSideName;
        private int level;

        private StoredBlock(final String kind,
                            final String text,
                            final String deviceApiName,
                            final String sideName,
                            final String targetDeviceApiName,
                            final String targetSideName,
                            final int level) {
            this.kind = kind;
            this.text = text;
            this.deviceApiName = deviceApiName;
            this.sideName = sideName;
            this.targetDeviceApiName = targetDeviceApiName;
            this.targetSideName = targetSideName;
            this.level = level;
        }
    }
}