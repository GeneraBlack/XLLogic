package de.xllogic.runtime.debug;

import de.xllogic.XLLogicMod;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class XLRuntimeDebugger {
    private static final Object LOCK = new Object();
    private static final int MAX_OVERLAY_SECTIONS = 6;
    private static final int MAX_OVERLAY_SPIKES = 3;
    private static final int MAX_LOG_SECTIONS = 12;
    private static final int MAX_LOG_SPIKES = 8;
    private static final int MAX_LOG_ACTIVE_SECTIONS = 8;
    private static final int MAX_LOG_THREADS = 8;
    private static final int MAX_THREAD_STACK_FRAMES = 10;
    private static final int MAX_SPIKES = 24;
    private static final long DEFAULT_SPIKE_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(2L);
    private static final long FRAME_HITCH_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(25L);
    private static final long CLIENT_TICK_HITCH_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(75L);
    private static final long AUTO_DUMP_COOLDOWN_MILLIS = 1000L;
    private static final long HITCH_MONITOR_INTERVAL_MILLIS = 25L;
    private static final String CLIENT_FRAME_GAP_SECTION = "client.frameGap";
    private static final String CLIENT_TICK_GAP_SECTION = "client.tickGap";
    private static final String THREAD_RENDER = "Render thread";
    private static final String THREAD_SERVER = "Server thread";
    private static final String THREAD_PYTHON_SESSION_PREFIX = "xllogic-python-session-";
    private static final String THREAD_PYTHON_WATCHDOG = "xllogic-python-watchdog";

    private static volatile boolean enabled;
    private static long lastFrameTimestampNanos;
    private static long lastClientTickTimestampNanos;
    private static long lastFrameGapNanos;
    private static long worstFrameGapNanos;
    private static long lastClientTickGapNanos;
    private static long worstClientTickGapNanos;
    private static long lastAutoDumpAtMillis;
    private static boolean frameStallDumped;
    private static boolean clientTickStallDumped;
    private static final Map<String, SectionStats> SECTION_STATS = new LinkedHashMap<>();
    private static final Deque<SpikeRecord> RECENT_SPIKES = new ArrayDeque<>();
    private static final Map<Long, Deque<ActiveSectionRecord>> ACTIVE_SECTION_STACKS = new LinkedHashMap<>();
    private static final ScheduledExecutorService HITCH_MONITOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "xllogic-runtime-debugger-monitor");
        thread.setDaemon(true);
        return thread;
    });

    static {
        HITCH_MONITOR.scheduleAtFixedRate(XLRuntimeDebugger::monitorHitches,
                HITCH_MONITOR_INTERVAL_MILLIS,
                HITCH_MONITOR_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private XLRuntimeDebugger() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean toggleEnabled() {
        synchronized (LOCK) {
            enabled = !enabled;
            if (enabled) {
                clearLocked();
            }
            return enabled;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            clearLocked();
        }
    }

    public static long beginSection(final String sectionKey) {
        if (!enabled || sectionKey == null || sectionKey.isBlank()) {
            return 0L;
        }
        final long startedAtNanos = System.nanoTime();
        final Thread currentThread = Thread.currentThread();
        synchronized (LOCK) {
            ACTIVE_SECTION_STACKS
                    .computeIfAbsent(currentThread.threadId(), ignored -> new ArrayDeque<>())
                    .addLast(new ActiveSectionRecord(sectionKey, startedAtNanos, currentThread.getName(), currentThread.threadId()));
        }
        return startedAtNanos;
    }

    public static void endSection(final String sectionKey, final long startedAtNanos) {
        if (startedAtNanos == 0L) {
            return;
        }
        clearActiveSection(sectionKey, startedAtNanos, Thread.currentThread().threadId());
        recordDuration(sectionKey, System.nanoTime() - startedAtNanos);
    }

    public static void recordDuration(final String sectionKey, final long durationNanos) {
        recordDuration(sectionKey, durationNanos, DEFAULT_SPIKE_THRESHOLD_NANOS);
    }

    public static void recordDuration(final String sectionKey, final long durationNanos, final long spikeThresholdNanos) {
        if (!enabled || sectionKey == null || sectionKey.isBlank() || durationNanos <= 0L) {
            return;
        }

        final String threadName = Thread.currentThread().getName();
        synchronized (LOCK) {
            SECTION_STATS.computeIfAbsent(sectionKey, SectionStats::new).addSample(durationNanos, threadName);
            if (durationNanos >= Math.max(0L, spikeThresholdNanos)) {
                recordSpikeLocked(new SpikeRecord(sectionKey, durationNanos, threadName));
            }
        }
    }

    public static void markClientFrame() {
        if (!enabled) {
            return;
        }

        final long now = System.nanoTime();
        final long nowMillis = System.currentTimeMillis();
        final String threadName = Thread.currentThread().getName();
        String autoDumpReason = null;
        synchronized (LOCK) {
            if (lastFrameTimestampNanos != 0L) {
                final long gapNanos = Math.max(0L, now - lastFrameTimestampNanos);
                lastFrameGapNanos = gapNanos;
                worstFrameGapNanos = Math.max(worstFrameGapNanos, gapNanos);
                SECTION_STATS.computeIfAbsent(CLIENT_FRAME_GAP_SECTION, SectionStats::new).addSample(gapNanos, threadName);
                if (gapNanos >= FRAME_HITCH_THRESHOLD_NANOS) {
                    recordSpikeLocked(new SpikeRecord(CLIENT_FRAME_GAP_SECTION, gapNanos, threadName));
                    autoDumpReason = scheduleAutoDumpLocked(CLIENT_FRAME_GAP_SECTION, gapNanos, nowMillis);
                }
            }
            lastFrameTimestampNanos = now;
            frameStallDumped = false;
        }
        if (autoDumpReason != null) {
            dumpToLog(autoDumpReason);
        }
    }

    public static void markClientTick() {
        if (!enabled) {
            return;
        }

        final long now = System.nanoTime();
        final long nowMillis = System.currentTimeMillis();
        final String threadName = Thread.currentThread().getName();
        String autoDumpReason = null;
        synchronized (LOCK) {
            if (lastClientTickTimestampNanos != 0L) {
                final long gapNanos = Math.max(0L, now - lastClientTickTimestampNanos);
                lastClientTickGapNanos = gapNanos;
                worstClientTickGapNanos = Math.max(worstClientTickGapNanos, gapNanos);
                SECTION_STATS.computeIfAbsent(CLIENT_TICK_GAP_SECTION, SectionStats::new).addSample(gapNanos, threadName);
                if (gapNanos >= CLIENT_TICK_HITCH_THRESHOLD_NANOS) {
                    recordSpikeLocked(new SpikeRecord(CLIENT_TICK_GAP_SECTION, gapNanos, threadName));
                    autoDumpReason = scheduleAutoDumpLocked(CLIENT_TICK_GAP_SECTION, gapNanos, nowMillis);
                }
            }
            lastClientTickTimestampNanos = now;
            clientTickStallDumped = false;
        }
        if (autoDumpReason != null) {
            dumpToLog(autoDumpReason);
        }
    }

    public static DebugSnapshot overlaySnapshot() {
        return snapshot(MAX_OVERLAY_SECTIONS, MAX_OVERLAY_SPIKES);
    }

    public static void dumpToLog() {
        dumpToLog(null);
    }

    public static void dumpToLog(final String triggerReason) {
        final DebugSnapshot snapshot = snapshot(MAX_LOG_SECTIONS, MAX_LOG_SPIKES);
        final List<ActiveSectionSnapshot> activeSections = activeSectionsSnapshot(MAX_LOG_ACTIVE_SECTIONS);
        final List<ThreadSnapshot> threads = interestingThreadSnapshots(MAX_LOG_THREADS, MAX_THREAD_STACK_FRAMES);
        if (triggerReason != null && !triggerReason.isBlank()) {
            XLLogicMod.LOGGER.info("XL runtime debugger auto dump triggered | {}", triggerReason);
        }
        logSummary(snapshot);
        logSections(snapshot.topSections());
        logSpikes(snapshot.latestSpikes());
        logActiveSections(activeSections);
        logThreads(threads);
    }

    private static void logSummary(final DebugSnapshot snapshot) {
        XLLogicMod.LOGGER.info("XL runtime debugger summary | frame last={} ms | frame worst={} ms | client tick last={} ms | client tick worst={} ms",
                formatMillis(snapshot.lastFrameGapNanos()),
                formatMillis(snapshot.worstFrameGapNanos()),
                formatMillis(snapshot.lastClientTickGapNanos()),
                formatMillis(snapshot.worstClientTickGapNanos()));
    }

    private static void logSections(final List<SectionSnapshot> sections) {
        if (sections.isEmpty()) {
            XLLogicMod.LOGGER.info("XL runtime debugger: no section samples captured yet.");
            return;
        }

        for (final SectionSnapshot section : sections) {
            XLLogicMod.LOGGER.info("XL runtime debugger section | max={} ms | avg={} ms | calls={} | thread={} | {}",
                    formatMillis(section.maxNanos()),
                    formatMillis(section.averageNanos()),
                    section.callCount(),
                    section.lastThread(),
                    section.name());
        }
    }

    private static void logSpikes(final List<SpikeSnapshot> spikes) {
        if (spikes.isEmpty()) {
            XLLogicMod.LOGGER.info("XL runtime debugger: no spike samples captured yet.");
            return;
        }

        for (final SpikeSnapshot spike : spikes) {
            XLLogicMod.LOGGER.info("XL runtime debugger spike | duration={} ms | thread={} | {}",
                    formatMillis(spike.durationNanos()),
                    spike.threadName(),
                    spike.name());
        }
    }

    private static void logActiveSections(final List<ActiveSectionSnapshot> activeSections) {
        if (activeSections.isEmpty()) {
            XLLogicMod.LOGGER.info("XL runtime debugger: no active sections at dump time.");
            return;
        }

        for (final ActiveSectionSnapshot activeSection : activeSections) {
            XLLogicMod.LOGGER.info("XL runtime debugger active section | elapsed={} ms | depth={} | thread={} | {}",
                    formatMillis(activeSection.elapsedNanos()),
                    activeSection.depth(),
                    activeSection.threadName(),
                    activeSection.name());
        }
    }

    private static void logThreads(final List<ThreadSnapshot> threads) {
        if (threads.isEmpty()) {
            XLLogicMod.LOGGER.info("XL runtime debugger: no interesting thread snapshots captured.");
            return;
        }

        for (final ThreadSnapshot thread : threads) {
            XLLogicMod.LOGGER.info("XL runtime debugger thread | state={} | blocked={} | waited={} | lock={} | owner={} | thread={}",
                    thread.state(),
                    thread.blockedCount(),
                    thread.waitedCount(),
                    thread.lockName(),
                    thread.lockOwnerName(),
                    thread.threadName());
            for (final String frame : thread.stackFrames()) {
                XLLogicMod.LOGGER.info("XL runtime debugger thread frame | thread={} | at {}", thread.threadName(), frame);
            }
        }
    }

    private static DebugSnapshot snapshot(final int sectionLimit, final int spikeLimit) {
        synchronized (LOCK) {
            final ArrayList<SectionSnapshot> sections = new ArrayList<>(SECTION_STATS.size());
            for (final SectionStats stats : SECTION_STATS.values()) {
                sections.add(stats.snapshot());
            }
            sections.sort(Comparator.<SectionSnapshot>comparingLong(SectionSnapshot::maxNanos).reversed()
                    .thenComparing(Comparator.comparingLong(SectionSnapshot::averageNanos).reversed())
                    .thenComparing(Comparator.comparingLong(SectionSnapshot::callCount).reversed())
                    .thenComparing(SectionSnapshot::name));
            if (sections.size() > sectionLimit) {
                sections.subList(sectionLimit, sections.size()).clear();
            }

            final ArrayList<SpikeSnapshot> spikes = new ArrayList<>(Math.min(spikeLimit, RECENT_SPIKES.size()));
            int emitted = 0;
            for (final SpikeRecord spike : RECENT_SPIKES.reversed()) {
                if (emitted >= spikeLimit) {
                    break;
                }
                spikes.add(spike.snapshot());
                emitted++;
            }

            return new DebugSnapshot(
                    lastFrameGapNanos,
                    worstFrameGapNanos,
                    lastClientTickGapNanos,
                    worstClientTickGapNanos,
                    List.copyOf(sections),
                    List.copyOf(spikes));
        }
    }

    private static void clearLocked() {
        SECTION_STATS.clear();
        RECENT_SPIKES.clear();
        ACTIVE_SECTION_STACKS.clear();
        lastFrameTimestampNanos = 0L;
        lastClientTickTimestampNanos = 0L;
        lastFrameGapNanos = 0L;
        worstFrameGapNanos = 0L;
        lastClientTickGapNanos = 0L;
        worstClientTickGapNanos = 0L;
        lastAutoDumpAtMillis = 0L;
        frameStallDumped = false;
        clientTickStallDumped = false;
    }

    private static void monitorHitches() {
        final String triggerReason;
        synchronized (LOCK) {
            if (!enabled) {
                return;
            }
            triggerReason = buildAsyncHitchReasonLocked(System.nanoTime(), System.currentTimeMillis());
        }
        if (triggerReason != null) {
            dumpToLog(triggerReason);
        }
    }

    private static String buildAsyncHitchReasonLocked(final long nowNanos, final long nowMillis) {
        final ArrayList<String> reasons = new ArrayList<>(2);
        boolean frameStallDetected = false;
        boolean clientTickStallDetected = false;

        if (lastFrameTimestampNanos != 0L && !frameStallDumped) {
            final long frameGapNanos = Math.max(0L, nowNanos - lastFrameTimestampNanos);
            if (frameGapNanos >= FRAME_HITCH_THRESHOLD_NANOS) {
                reasons.add(CLIENT_FRAME_GAP_SECTION + ".stall=" + formatMillis(frameGapNanos) + " ms");
                frameStallDetected = true;
            }
        }

        if (lastClientTickTimestampNanos != 0L && !clientTickStallDumped) {
            final long clientTickGapNanos = Math.max(0L, nowNanos - lastClientTickTimestampNanos);
            if (clientTickGapNanos >= CLIENT_TICK_HITCH_THRESHOLD_NANOS) {
                reasons.add(CLIENT_TICK_GAP_SECTION + ".stall=" + formatMillis(clientTickGapNanos) + " ms");
                clientTickStallDetected = true;
            }
        }

        if (reasons.isEmpty() || nowMillis - lastAutoDumpAtMillis < AUTO_DUMP_COOLDOWN_MILLIS) {
            return null;
        }

        if (frameStallDetected) {
            frameStallDumped = true;
        }
        if (clientTickStallDetected) {
            clientTickStallDumped = true;
        }
        lastAutoDumpAtMillis = nowMillis;
        return "async " + String.join(" | ", reasons);
    }

    private static void clearActiveSection(final String sectionKey, final long startedAtNanos, final long threadId) {
        synchronized (LOCK) {
            clearActiveSectionLocked(sectionKey, startedAtNanos, threadId);
        }
    }

    private static void clearActiveSectionLocked(final String sectionKey, final long startedAtNanos, final long threadId) {
        final Deque<ActiveSectionRecord> stack = ACTIVE_SECTION_STACKS.get(threadId);
        if (stack == null || stack.isEmpty()) {
            return;
        }

        final var iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            final ActiveSectionRecord activeSection = iterator.next();
            if (activeSection.startedAtNanos() == startedAtNanos && activeSection.name().equals(sectionKey)) {
                iterator.remove();
                break;
            }
        }

        if (stack.isEmpty()) {
            ACTIVE_SECTION_STACKS.remove(threadId);
        }
    }

    private static List<ActiveSectionSnapshot> activeSectionsSnapshot(final int limit) {
        synchronized (LOCK) {
            final long nowNanos = System.nanoTime();
            final ArrayList<ActiveSectionSnapshot> activeSections = new ArrayList<>();
            for (final Deque<ActiveSectionRecord> stack : ACTIVE_SECTION_STACKS.values()) {
                int depth = 1;
                for (final ActiveSectionRecord activeSection : stack) {
                    activeSections.add(new ActiveSectionSnapshot(
                            activeSection.name(),
                            activeSection.threadName(),
                            depth,
                            Math.max(0L, nowNanos - activeSection.startedAtNanos())));
                    depth++;
                }
            }

            activeSections.sort(Comparator.<ActiveSectionSnapshot>comparingLong(ActiveSectionSnapshot::elapsedNanos).reversed()
                    .thenComparingInt(ActiveSectionSnapshot::depth)
                    .thenComparing(ActiveSectionSnapshot::threadName)
                    .thenComparing(ActiveSectionSnapshot::name));
            if (activeSections.size() > limit) {
                activeSections.subList(limit, activeSections.size()).clear();
            }
            return List.copyOf(activeSections);
        }
    }

    private static List<ThreadSnapshot> interestingThreadSnapshots(final int threadLimit, final int frameLimit) {
        final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        final ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(true, true);
        final ArrayList<ThreadSnapshot> threads = new ArrayList<>();
        for (final ThreadInfo threadInfo : threadInfos) {
            if (threadInfo == null || !isInterestingThread(threadInfo.getThreadName())) {
                continue;
            }

            final ArrayList<String> stackFrames = new ArrayList<>();
            final StackTraceElement[] stackTrace = threadInfo.getStackTrace();
            for (int index = 0; index < Math.min(frameLimit, stackTrace.length); index++) {
                stackFrames.add(stackTrace[index].toString());
            }

            threads.add(new ThreadSnapshot(
                    threadInfo.getThreadName(),
                    String.valueOf(threadInfo.getThreadState()),
                    threadInfo.getBlockedCount(),
                    threadInfo.getWaitedCount(),
                    safeString(threadInfo.getLockName()),
                    safeString(threadInfo.getLockOwnerName()),
                    List.copyOf(stackFrames)));
        }

        threads.sort(Comparator.<ThreadSnapshot>comparingInt(thread -> interestingThreadPriority(thread.threadName()))
                .thenComparing(ThreadSnapshot::threadName));
        if (threads.size() > threadLimit) {
            threads.subList(threadLimit, threads.size()).clear();
        }
        return List.copyOf(threads);
    }

    private static boolean isInterestingThread(final String threadName) {
        if (threadName == null || threadName.isBlank()) {
            return false;
        }
        return THREAD_RENDER.equals(threadName)
                || THREAD_SERVER.equals(threadName)
                || THREAD_PYTHON_WATCHDOG.equals(threadName)
                || threadName.startsWith(THREAD_PYTHON_SESSION_PREFIX);
    }

    private static int interestingThreadPriority(final String threadName) {
        if (THREAD_RENDER.equals(threadName)) {
            return 0;
        }
        if (THREAD_SERVER.equals(threadName)) {
            return 1;
        }
        if (threadName != null && threadName.startsWith(THREAD_PYTHON_SESSION_PREFIX)) {
            return 2;
        }
        if (THREAD_PYTHON_WATCHDOG.equals(threadName)) {
            return 3;
        }
        return 4;
    }

    private static String safeString(final String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String scheduleAutoDumpLocked(final String sectionKey, final long durationNanos, final long nowMillis) {
        if (nowMillis - lastAutoDumpAtMillis < AUTO_DUMP_COOLDOWN_MILLIS) {
            return null;
        }
        lastAutoDumpAtMillis = nowMillis;
        return sectionKey + "=" + formatMillis(durationNanos) + " ms";
    }

    private static void recordSpikeLocked(final SpikeRecord spike) {
        while (RECENT_SPIKES.size() >= MAX_SPIKES) {
            RECENT_SPIKES.removeFirst();
        }
        RECENT_SPIKES.addLast(spike);
    }

    private static String formatMillis(final long durationNanos) {
        return String.format(Locale.ROOT, "%.2f", durationNanos / 1_000_000.0d);
    }

    public record DebugSnapshot(long lastFrameGapNanos,
                                long worstFrameGapNanos,
                                long lastClientTickGapNanos,
                                long worstClientTickGapNanos,
                                List<SectionSnapshot> topSections,
                                List<SpikeSnapshot> latestSpikes) {
    }

    public record SectionSnapshot(String name,
                                  long callCount,
                                  long averageNanos,
                                  long maxNanos,
                                  long lastNanos,
                                  String lastThread) {
    }

    public record SpikeSnapshot(String name, long durationNanos, String threadName) {
    }

    public record ActiveSectionSnapshot(String name, String threadName, int depth, long elapsedNanos) {
    }

    public record ThreadSnapshot(String threadName,
                                 String state,
                                 long blockedCount,
                                 long waitedCount,
                                 String lockName,
                                 String lockOwnerName,
                                 List<String> stackFrames) {
    }

    private static final class SectionStats {
        private final String name;
        private long callCount;
        private long totalNanos;
        private long maxNanos;
        private long lastNanos;
        private String lastThread = "";

        private SectionStats(final String name) {
            this.name = name;
        }

        private void addSample(final long durationNanos, final String threadName) {
            this.callCount++;
            this.totalNanos += durationNanos;
            this.maxNanos = Math.max(this.maxNanos, durationNanos);
            this.lastNanos = durationNanos;
            this.lastThread = threadName == null ? "" : threadName;
        }

        private SectionSnapshot snapshot() {
            final long averageNanos = this.callCount <= 0L ? 0L : this.totalNanos / this.callCount;
            return new SectionSnapshot(this.name, this.callCount, averageNanos, this.maxNanos, this.lastNanos, this.lastThread);
        }
    }

    private record SpikeRecord(String name, long durationNanos, String threadName) {
        private SpikeSnapshot snapshot() {
            return new SpikeSnapshot(this.name, this.durationNanos, this.threadName);
        }
    }

    private record ActiveSectionRecord(String name, long startedAtNanos, String threadName, long threadId) {
    }
}