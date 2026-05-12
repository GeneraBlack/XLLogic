package de.xllogic.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ComputerOutputEntry(String tone, String channel, String kind, String title, String text, String payloadJson) {
    private static final int MAX_TONE_LENGTH = 16;
    private static final int MAX_CHANNEL_LENGTH = 24;
    private static final int MAX_KIND_LENGTH = 24;
    private static final int MAX_TITLE_LENGTH = 96;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_PAYLOAD_LENGTH = 4096;
    private static final String TONE_INFO = "info";
    private static final String TONE_OK = "ok";
    private static final String TONE_ERROR = "error";
    private static final String CHANNEL_INFO = "info";
    private static final String CHANNEL_PLAN = "plan";
    private static final String CHANNEL_STDOUT = "stdout";
    private static final String CHANNEL_STDERR = "stderr";
    private static final String KIND_LINE = "line";
    private static final String KIND_KEY_VALUE = "key_value";
    private static final String KIND_TABLE = "table";
    private static final String KIND_PLAN_CARD = "plan_card";

    public static final StreamCodec<ByteBuf, ComputerOutputEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_TONE_LENGTH),
            ComputerOutputEntry::tone,
            ByteBufCodecs.stringUtf8(MAX_CHANNEL_LENGTH),
            ComputerOutputEntry::channel,
            ByteBufCodecs.stringUtf8(MAX_KIND_LENGTH),
            ComputerOutputEntry::kind,
            ByteBufCodecs.stringUtf8(MAX_TITLE_LENGTH),
            ComputerOutputEntry::title,
            ByteBufCodecs.stringUtf8(MAX_TEXT_LENGTH),
            ComputerOutputEntry::text,
            ByteBufCodecs.stringUtf8(MAX_PAYLOAD_LENGTH),
            ComputerOutputEntry::payloadJson,
            ComputerOutputEntry::new
    );

    public ComputerOutputEntry {
        tone = normalizeTone(tone);
        channel = normalizeChannel(channel);
        kind = normalizeKind(kind);
        title = limit(title, MAX_TITLE_LENGTH);
        text = limit(text, MAX_TEXT_LENGTH);
        payloadJson = limit(payloadJson, MAX_PAYLOAD_LENGTH);
    }

    public static ComputerOutputEntry info(final String text) {
        return line(TONE_INFO, CHANNEL_INFO, text);
    }

    public static ComputerOutputEntry ok(final String text) {
        return line(TONE_OK, TONE_OK, text);
    }

    public static ComputerOutputEntry error(final String text) {
        return line(TONE_ERROR, TONE_ERROR, text);
    }

    public static ComputerOutputEntry hint(final String text) {
        return line(TONE_INFO, "hint", text);
    }

    public static ComputerOutputEntry stdout(final String text) {
        return line(TONE_INFO, CHANNEL_STDOUT, text);
    }

    public static ComputerOutputEntry stderr(final String text) {
        return line(TONE_ERROR, CHANNEL_STDERR, text);
    }

    public static ComputerOutputEntry plan(final String tone, final String text) {
        return line(tone, CHANNEL_PLAN, text);
    }

    public static ComputerOutputEntry line(final String tone, final String channel, final String text) {
        return new ComputerOutputEntry(tone, channel, KIND_LINE, "", text, "");
    }

    public static ComputerOutputEntry structured(final String tone, final String channel, final String kind, final String title, final String text, final String payloadJson) {
        return new ComputerOutputEntry(tone, channel, kind, title, text, payloadJson);
    }

    public static ComputerOutputEntry keyValue(final String tone, final String channel, final String title, final String text, final List<OutputField> fields) {
        return structured(tone, channel, KIND_KEY_VALUE, title, text, encodeFields(fields));
    }

    public static ComputerOutputEntry table(final String tone, final String channel, final String title, final String text, final List<String> columns, final List<List<String>> rows) {
        return structured(tone, channel, KIND_TABLE, title, text, encodeTable(columns, rows));
    }

    public static ComputerOutputEntry planCard(final String tone, final String title, final String text, final List<OutputField> fields) {
        return structured(tone, CHANNEL_PLAN, KIND_PLAN_CARD, title, text, encodeFields(fields));
    }

    public static ComputerOutputEntry fromLegacyLine(final String line) {
        final String safeLine = line == null ? "" : line;
        if (safeLine.startsWith("[stdout] ")) {
            return stdout(safeLine.substring(9));
        }
        if (safeLine.startsWith("[stderr] ")) {
            return stderr(safeLine.substring(9));
        }
        if (safeLine.startsWith("[ok] [plan] ")) {
            return plan(TONE_OK, safeLine.substring(12));
        }
        if (safeLine.startsWith("[error] [plan] ")) {
            return plan(TONE_ERROR, safeLine.substring(15));
        }
        if (safeLine.startsWith("[info] [plan] ")) {
            return plan(TONE_INFO, safeLine.substring(14));
        }
        if (safeLine.startsWith("[ok] ")) {
            return ok(safeLine.substring(5));
        }
        if (safeLine.startsWith("[error] ")) {
            return error(safeLine.substring(8));
        }
        if (safeLine.startsWith("[info] ")) {
            return info(safeLine.substring(7));
        }
        return info(safeLine);
    }

    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Tone", this.tone);
        tag.putString("Channel", this.channel);
        tag.putString("Kind", this.kind);
        tag.putString("Title", this.title);
        tag.putString("Text", this.text);
        tag.putString("PayloadJson", this.payloadJson);
        return tag;
    }

    public static ComputerOutputEntry fromTag(final CompoundTag tag) {
        return new ComputerOutputEntry(
                tag.getString("Tone"),
                tag.getString("Channel"),
                tag.contains("Kind") ? tag.getString("Kind") : KIND_LINE,
                tag.contains("Title") ? tag.getString("Title") : "",
                tag.getString("Text"),
                tag.contains("PayloadJson") ? tag.getString("PayloadJson") : ""
        );
    }

    public boolean okTone() {
        return TONE_OK.equals(this.tone);
    }

    public boolean errorTone() {
        return TONE_ERROR.equals(this.tone);
    }

    public boolean planChannel() {
        return CHANNEL_PLAN.equals(this.channel);
    }

    public boolean lineKind() {
        return KIND_LINE.equals(this.kind);
    }

    public boolean keyValueKind() {
        return KIND_KEY_VALUE.equals(this.kind);
    }

    public boolean tableKind() {
        return KIND_TABLE.equals(this.kind);
    }

    public boolean planCardKind() {
        return KIND_PLAN_CARD.equals(this.kind);
    }

    public String displayLabel() {
        if (this.tableKind()) {
            return "TABLE";
        }
        if (this.keyValueKind()) {
            return "KV";
        }
        if (this.planCardKind()) {
            return "PLAN";
        }
        return this.channel.toUpperCase(Locale.ROOT);
    }

    public String formattedLine() {
        if (!this.lineKind()) {
            return this.tonePrefix() + "[" + this.kind + "] " + this.summaryLine();
        }
        return switch (this.channel) {
            case CHANNEL_STDOUT -> "[stdout] " + this.text;
            case CHANNEL_STDERR -> "[stderr] " + this.text;
            case CHANNEL_PLAN -> this.tonePrefix() + "[plan] " + this.text;
            default -> this.tonePrefix() + this.text;
        };
    }

    public String summaryLine() {
        final StringBuilder builder = new StringBuilder();
        if (!this.title.isBlank()) {
            builder.append(this.title);
        }
        if (!this.text.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(this.text);
        }
        return builder.length() == 0 ? this.displayLabel() : builder.toString();
    }

    public List<OutputField> fields() {
        if (!this.keyValueKind() && !this.planCardKind()) {
            return List.of();
        }

        final JsonElement payload = this.parsePayload();
        if (payload == null || !payload.isJsonArray()) {
            return List.of();
        }

        final ArrayList<OutputField> fields = new ArrayList<>();
        for (final JsonElement element : payload.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }

            final JsonObject object = element.getAsJsonObject();
            fields.add(new OutputField(stringProperty(object, "key"), stringProperty(object, "value")));
        }
        return List.copyOf(fields);
    }

    public TableData tableData() {
        if (!this.tableKind()) {
            return TableData.empty();
        }

        final JsonElement payload = this.parsePayload();
        if (payload == null || !payload.isJsonObject()) {
            return TableData.empty();
        }

        final JsonObject object = payload.getAsJsonObject();
        final ArrayList<String> columns = new ArrayList<>();
        if (object.has("columns") && object.get("columns").isJsonArray()) {
            for (final JsonElement column : object.getAsJsonArray("columns")) {
                columns.add(asString(column));
            }
        }

        final ArrayList<List<String>> rows = new ArrayList<>();
        if (object.has("rows") && object.get("rows").isJsonArray()) {
            for (final JsonElement rowElement : object.getAsJsonArray("rows")) {
                if (!rowElement.isJsonArray()) {
                    continue;
                }

                final ArrayList<String> row = new ArrayList<>();
                for (final JsonElement cell : rowElement.getAsJsonArray()) {
                    row.add(asString(cell));
                }
                rows.add(List.copyOf(row));
            }
        }

        return new TableData(List.copyOf(columns), List.copyOf(rows));
    }

    private String tonePrefix() {
        return switch (this.tone) {
            case TONE_OK -> "[ok] ";
            case TONE_ERROR -> "[error] ";
            default -> "[info] ";
        };
    }

    private static String normalizeTone(final String tone) {
        final String normalized = limit(tone == null ? "" : tone.toLowerCase(Locale.ROOT), MAX_TONE_LENGTH);
        return switch (normalized) {
            case TONE_OK, TONE_ERROR -> normalized;
            default -> TONE_INFO;
        };
    }

    private static String normalizeChannel(final String channel) {
        final String normalized = limit(channel == null ? "" : channel.toLowerCase(Locale.ROOT), MAX_CHANNEL_LENGTH);
        return normalized.isBlank() ? CHANNEL_INFO : normalized;
    }

    private static String normalizeKind(final String kind) {
        final String normalized = limit(kind == null ? "" : kind.toLowerCase(Locale.ROOT), MAX_KIND_LENGTH);
        return switch (normalized) {
            case KIND_KEY_VALUE, KIND_TABLE, KIND_PLAN_CARD -> normalized;
            default -> KIND_LINE;
        };
    }

    private JsonElement parsePayload() {
        if (this.payloadJson.isBlank()) {
            return null;
        }

        try {
            return JsonParser.parseString(this.payloadJson);
        } catch (final RuntimeException exception) {
            return null;
        }
    }

    private static String encodeFields(final List<OutputField> fields) {
        final JsonArray payload = new JsonArray();
        if (fields != null) {
            for (final OutputField field : fields) {
                if (field == null) {
                    continue;
                }
                final JsonObject object = new JsonObject();
                object.addProperty("key", field.key());
                object.addProperty("value", field.value());
                payload.add(object);
            }
        }
        return payload.toString();
    }

    private static String encodeTable(final List<String> columns, final List<List<String>> rows) {
        final JsonObject payload = new JsonObject();
        final JsonArray columnArray = new JsonArray();
        if (columns != null) {
            for (final String column : columns) {
                columnArray.add(column == null ? "" : column);
            }
        }
        payload.add("columns", columnArray);

        final JsonArray rowArray = new JsonArray();
        if (rows != null) {
            for (final List<String> row : rows) {
                final JsonArray cells = new JsonArray();
                if (row != null) {
                    for (final String value : row) {
                        cells.add(value == null ? "" : value);
                    }
                }
                rowArray.add(cells);
            }
        }
        payload.add("rows", rowArray);
        return payload.toString();
    }

    private static String stringProperty(final JsonObject object, final String name) {
        return object.has(name) ? asString(object.get(name)) : "";
    }

    private static String asString(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return limit(element.getAsString(), MAX_TEXT_LENGTH);
        }
        return limit(element.toString(), MAX_TEXT_LENGTH);
    }

    private static String limit(final String value, final int maxLength) {
        final String safeValue = value == null ? "" : value;
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    public record OutputField(String key, String value) {
        public OutputField {
            key = Objects.requireNonNullElse(key, "");
            value = Objects.requireNonNullElse(value, "");
        }
    }

    public record TableData(List<String> columns, List<List<String>> rows) {
        public TableData {
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public static TableData empty() {
            return new TableData(List.of(), List.of());
        }
    }
}