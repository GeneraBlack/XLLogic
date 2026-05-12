package de.xllogic.runtime;

final class OutputLimitExceededException extends RuntimeException {
    private final String channelName;
    private final long maxBytes;

    OutputLimitExceededException(final String channelName, final long maxBytes) {
        super(channelName == null || channelName.isBlank()
                ? "Python output exceeded a configured byte limit."
                : channelName + " exceeded the configured byte limit of " + maxBytes + " B.");
        this.channelName = channelName == null ? "" : channelName;
        this.maxBytes = Math.max(0L, maxBytes);
    }

    String summary() {
        if ("stderr".equals(this.channelName)) {
            return "Execution stopped after exceeding the configured stderr limit of " + this.maxBytes + " B.";
        }
        return "Execution stopped after exceeding the configured stdout limit of " + this.maxBytes + " B.";
    }
}