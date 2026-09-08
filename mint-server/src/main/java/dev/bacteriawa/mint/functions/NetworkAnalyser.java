package dev.bacteriawa.mint.functions;

import net.minecraft.network.Connection;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class NetworkAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkAnalyser.class);
    private static ScheduledExecutorService scheduledExecutor = null;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static volatile boolean running = false;
    private static volatile long startTime = 0;
    private static volatile long stopTime = 0;
    
    private static final int MAX_TRACKED_CONNECTIONS = 10000;

    private static final ConcurrentHashMap<String, ConcurrentHashMap<Connection, Boolean>> packetConnections = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> packetCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> packetSizes = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, LongAdder> receivedPacketCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> receivedPacketSizes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> sentPacketCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> sentPacketSizes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Connection, LongAdder> connectionPacketCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Connection, LongAdder> connectionPacketSizes = new ConcurrentHashMap<>();
    private static final AtomicInteger trackedConnections = new AtomicInteger(0);
    
    public static boolean start() {
        if (running) return false;
        reset();
        running = true;
        return true;
    }
    
    public static boolean stop() {
        if (!running) return false;
        running = false;
        stopTime = System.currentTimeMillis();
        return true;
    }
    
    public static void reset() {
        startTime = System.currentTimeMillis();
        packetCounts.clear();
        packetSizes.clear();
        packetConnections.clear();
        receivedPacketCounts.clear();
        receivedPacketSizes.clear();
        sentPacketCounts.clear();
        sentPacketSizes.clear();
        connectionPacketCounts.clear();
        connectionPacketSizes.clear();
        trackedConnections.set(0);
    }
    
    public static void onPacketReceived(Connection connection, String packetType, int size) {
        if (!running || connection == null || packetType == null) return;
        
        try {
            addPacketStats(packetCounts, packetSizes, packetType, size);
            addPacketStats(receivedPacketCounts, receivedPacketSizes, packetType, size);

            if (trackConnection(connection)) {
                LongAdder countAdder = connectionPacketCounts.get(connection);
                LongAdder sizeAdder = connectionPacketSizes.get(connection);
                if (countAdder != null) {
                    countAdder.increment();
                }
                if (sizeAdder != null) {
                    sizeAdder.add(size);
                }

                packetConnections.computeIfAbsent(packetType, s -> new ConcurrentHashMap<>())
                    .putIfAbsent(connection, Boolean.TRUE);
            }
        } catch (Exception e) {
        }
    }
    
    public static void onPacketSent(Connection connection, String packetType, int size) {
        if (!running || connection == null || packetType == null) return;
        
        try {
            addPacketStats(packetCounts, packetSizes, packetType, size);
            addPacketStats(sentPacketCounts, sentPacketSizes, packetType, size);

            if (trackConnection(connection)) {
                LongAdder countAdder = connectionPacketCounts.get(connection);
                LongAdder sizeAdder = connectionPacketSizes.get(connection);
                if (countAdder != null) {
                    countAdder.increment();
                }
                if (sizeAdder != null) {
                    sizeAdder.add(size);
                }

                packetConnections.computeIfAbsent(packetType, s -> new ConcurrentHashMap<>())
                    .putIfAbsent(connection, Boolean.TRUE);
            }
        } catch (Exception e) {
        }
    }
    
    public static boolean isRunning() {
        return running;
    }
    
    public static long getRunningTime() {
        return (running ? System.currentTimeMillis() - startTime : stopTime - startTime);
    }
    
    public static Map<String, Long> getSortedPacketSizes() {
        return getSortedLongMap(packetSizes);
    }
    
    public static Map<String, Long> getSortedReceivedPacketSizes() {
        return getSortedLongMap(receivedPacketSizes);
    }
    
    public static Map<String, Long> getSortedSentPacketSizes() {
        return getSortedLongMap(sentPacketSizes);
    }
    
    public static Map<Connection, Integer> getSortedConnectionPacketCounts() {
        return getSortedConnectionMap(connectionPacketCounts);
    }
    
    public static Map<Connection, Integer> getSortedConnectionPacketSizes() {
        return getSortedConnectionMap(connectionPacketSizes);
    }
    
    public static long getPacketCount(String packetType) {
        LongAdder adder = packetCounts.get(packetType);
        return adder == null ? 0L : adder.sum();
    }
    
    public static long getReceivedPacketCount(String packetType) {
        LongAdder adder = receivedPacketCounts.get(packetType);
        return adder == null ? 0L : adder.sum();
    }
    
    public static long getSentPacketCount(String packetType) {
        LongAdder adder = sentPacketCounts.get(packetType);
        return adder == null ? 0L : adder.sum();
    }
    
    public static long getPacketSize(String packetType) {
        LongAdder adder = packetSizes.get(packetType);
        return adder == null ? 0L : adder.sum();
    }
    
    public static long getReceivedPacketSize(String packetType) {
        LongAdder adder = receivedPacketSizes.get(packetType);
        return adder == null ? 0L : adder.sum();
    }
    
    public static long getSentPacketSize(String packetType) {
        LongAdder adder = sentPacketSizes.get(packetType);
        return adder == null ? 0L : adder.sum();
    }
    
    public static int getConnectionPacketCount(Connection connection) {
        LongAdder adder = connectionPacketCounts.get(connection);
        return adder == null ? 0 : (int) adder.sum();
    }
    
    public static int getConnectionPacketSize(Connection connection) {
        LongAdder adder = connectionPacketSizes.get(connection);
        return adder == null ? 0 : (int) adder.sum();
    }
    
    public static boolean isEmpty() {
        return packetCounts.isEmpty();
    }
    
    public static Map<String, Long> getAllPacketCounts() {
        return snapshotLongMap(packetCounts);
    }
    
    public static Map<String, Long> getAllPacketSizes() {
        return snapshotLongMap(packetSizes);
    }
    
    public static void removeConnection(Connection connection) {
        if (connection == null) return;
        
        try {
            if (connectionPacketCounts.remove(connection) != null) {
                trackedConnections.decrementAndGet();
            }
            connectionPacketSizes.remove(connection);
            
            for (ConcurrentHashMap<Connection, Boolean> map : packetConnections.values()) {
                if (map != null) {
                    map.remove(connection);
                }
            }
        } catch (Exception e) {
        }
    }
    
    public static int getActiveConnectionCount() {
        return connectionPacketCounts.size();
    }
    
    public static long getTotalPacketCount() {
        return sumValues(packetCounts);
    }
    
    public static long getTotalPacketSize() {
        return sumValues(packetSizes);
    }
    
    public static long getTotalReceivedPacketCount() {
        return sumValues(receivedPacketCounts);
    }
    
    public static long getTotalReceivedPacketSize() {
        return sumValues(receivedPacketSizes);
    }
    
    public static long getTotalSentPacketCount() {
        return sumValues(sentPacketCounts);
    }
    
    public static long getTotalSentPacketSize() {
        return sumValues(sentPacketSizes);
    }
    
    public static double getAveragePacketsPerSecond() {
        long runtime = getRunningTime();
        if (runtime == 0) return 0;
        return (getTotalPacketCount() * 1000.0) / runtime;
    }
    
    public static double getAverageBytesPerSecond() {
        long runtime = getRunningTime();
        if (runtime == 0) return 0;
        return (getTotalPacketSize() * 1000.0) / runtime;
    }
    
    public static java.util.Set<String> getPacketTypesForConnection(Connection connection) {
        java.util.Set<String> types = new HashSet<>();
        for (Map.Entry<String, ConcurrentHashMap<Connection, Boolean>> entry : packetConnections.entrySet()) {
            if (entry.getValue().containsKey(connection)) {
                types.add(entry.getKey());
            }
        }
        return types;
    }

    private static void addPacketStats(ConcurrentHashMap<String, LongAdder> counts,
                                       ConcurrentHashMap<String, LongAdder> sizes,
                                       String packetType,
                                       int size) {
        counts.computeIfAbsent(packetType, key -> new LongAdder()).increment();
        sizes.computeIfAbsent(packetType, key -> new LongAdder()).add(size);
    }

    private static boolean trackConnection(Connection connection) {
        if (connectionPacketCounts.containsKey(connection)) {
            return true;
        }
        if (trackedConnections.get() >= MAX_TRACKED_CONNECTIONS) {
            return false;
        }
        if (connectionPacketCounts.putIfAbsent(connection, new LongAdder()) == null) {
            trackedConnections.incrementAndGet();
        }
        connectionPacketSizes.putIfAbsent(connection, new LongAdder());
        return true;
    }

    private static Map<String, Long> getSortedLongMap(ConcurrentHashMap<String, LongAdder> map) {
        return map.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().sum(),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    private static Map<Connection, Integer> getSortedConnectionMap(ConcurrentHashMap<Connection, LongAdder> map) {
        return map.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> (int) entry.getValue().sum(),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    private static Map<String, Long> snapshotLongMap(ConcurrentHashMap<String, LongAdder> map) {
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<String, LongAdder> entry : map.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return snapshot;
    }

    private static long sumValues(ConcurrentHashMap<?, LongAdder> map) {
        long total = 0L;
        for (LongAdder adder : map.values()) {
            total += adder.sum();
        }
        return total;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    public static String formatBps(long bps) {
        if (bps < 1000) {
            return bps + " bps";
        } else if (bps < 1000000) {
            return String.format(Locale.ROOT, "%.2f Kbps", bps / 1000.0);
        } else {
            return String.format(Locale.ROOT, "%.2f Mbps", bps / 1000000.0);
        }
    }

    public static String generateReportText(int topLimit) {
        long duration = getRunningTime();
        double durationSec = duration / 1000.0;
        long totalPackets = getTotalPacketCount();
        long totalBytes = getTotalPacketSize();
        double avgPps = getAveragePacketsPerSecond();
        double avgBps = getAverageBytesPerSecond();
        long rxPackets = getTotalReceivedPacketCount();
        long rxBytes = getTotalReceivedPacketSize();
        long txPackets = getTotalSentPacketCount();
        long txBytes = getTotalSentPacketSize();

        StringBuilder sb = new StringBuilder();
        sb.append("**Network Analyser Report**\n");
        sb.append(String.format(Locale.ROOT, "• **Duration:** %.2fs (%.2f min)\n", durationSec, durationSec / 60.0));
        sb.append(String.format(Locale.ROOT, "• **Total Packets:** %,d (%.1f pps)\n", totalPackets, avgPps));
        sb.append(String.format(Locale.ROOT, "  - Received: %,d (%s)\n", rxPackets, formatBytes(rxBytes)));
        sb.append(String.format(Locale.ROOT, "  - Sent: %,d (%s)\n", txPackets, formatBytes(txBytes)));
        sb.append(String.format(Locale.ROOT, "• **Total Traffic:** %s (%s)\n\n", formatBytes(totalBytes), formatBps((long) avgBps * 8)));

        sb.append(String.format(Locale.ROOT, "**Top %d Packet Types by Size:**\n```\n", topLimit));
        sb.append(String.format(Locale.ROOT, "%-4s %-32s %-10s %s\n", "Rank", "Packet Type", "Count", "Size"));
        sb.append("------------------------------------------------------------\n");

        Map<String, Long> sortedPackets = getSortedPacketSizes();
        int rank = 1;
        for (Map.Entry<String, Long> entry : sortedPackets.entrySet()) {
            if (rank > topLimit) break;
            String type = entry.getKey();
            long size = entry.getValue();
            long count = getPacketCount(type);
            String displayType = type.length() > 32 ? type.substring(0, 29) + "..." : type;
            sb.append(String.format(Locale.ROOT, "#%-3d %-32s %-10d %s\n", rank, displayType, count, formatBytes(size)));
            rank++;
        }
        sb.append("```");
        return sb.toString();
    }

    public static String buildDiscordWebhookPayload(String title, String reportText, int topLimit) {
        JsonObject root = new JsonObject();
        root.addProperty("username", "Mint Network Analyser");
        root.addProperty("content", reportText);

        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title != null && !title.isBlank() ? title : "Mint Network Analyser Report");
        embed.addProperty("color", 0x06D094); // Mint accent color

        long duration = getRunningTime();
        double durationSec = duration / 1000.0;
        long totalPackets = getTotalPacketCount();
        long totalBytes = getTotalPacketSize();
        double avgPps = getAveragePacketsPerSecond();
        double avgBps = getAverageBytesPerSecond();
        long rxPackets = getTotalReceivedPacketCount();
        long rxBytes = getTotalReceivedPacketSize();
        long txPackets = getTotalSentPacketCount();
        long txBytes = getTotalSentPacketSize();

        JsonArray fields = new JsonArray();

        JsonObject fDuration = new JsonObject();
        fDuration.addProperty("name", "Duration");
        fDuration.addProperty("value", String.format(Locale.ROOT, "%.1f min (%.0fs)", durationSec / 60.0, durationSec));
        fDuration.addProperty("inline", true);
        fields.add(fDuration);

        JsonObject fPackets = new JsonObject();
        fPackets.addProperty("name", "Total Packets");
        fPackets.addProperty("value", String.format(Locale.ROOT, "%,d\n(%.1f pps)", totalPackets, avgPps));
        fPackets.addProperty("inline", true);
        fields.add(fPackets);

        JsonObject fTraffic = new JsonObject();
        fTraffic.addProperty("name", "Total Traffic");
        fTraffic.addProperty("value", String.format(Locale.ROOT, "%s\n(%s)", formatBytes(totalBytes), formatBps((long) avgBps * 8)));
        fTraffic.addProperty("inline", true);
        fields.add(fTraffic);

        JsonObject fReceived = new JsonObject();
        fReceived.addProperty("name", "Received (In)");
        fReceived.addProperty("value", String.format(Locale.ROOT, "%,d packets\n%s", rxPackets, formatBytes(rxBytes)));
        fReceived.addProperty("inline", true);
        fields.add(fReceived);

        JsonObject fSent = new JsonObject();
        fSent.addProperty("name", "Sent (Out)");
        fSent.addProperty("value", String.format(Locale.ROOT, "%,d packets\n%s", txPackets, formatBytes(txBytes)));
        fSent.addProperty("inline", true);
        fields.add(fSent);

        StringBuilder topTable = new StringBuilder();
        topTable.append("```\n");
        topTable.append(String.format(Locale.ROOT, "%-4s %-28s %-8s %s\n", "Rank", "Type", "Count", "Size"));
        Map<String, Long> sortedPackets = getSortedPacketSizes();
        int rank = 1;
        for (Map.Entry<String, Long> entry : sortedPackets.entrySet()) {
            if (rank > topLimit) break;
            String type = entry.getKey();
            long size = entry.getValue();
            long count = getPacketCount(type);
            String displayType = type.length() > 28 ? type.substring(0, 25) + "..." : type;
            topTable.append(String.format(Locale.ROOT, "#%-3d %-28s %-8d %s\n", rank, displayType, count, formatBytes(size)));
            rank++;
        }
        topTable.append("```");

        JsonObject fTop = new JsonObject();
        fTop.addProperty("name", "Top Packets");
        fTop.addProperty("value", topTable.toString());
        fTop.addProperty("inline", false);
        fields.add(fTop);

        embed.add("fields", fields);
        embed.addProperty("timestamp", Instant.now().toString());

        embeds.add(embed);
        root.add("embeds", embeds);

        return new Gson().toJson(root);
    }

    public static void sendWebhookReport(String webhookUrl, String title, int topLimit) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.warn("[NetworkAnalyser] Webhook URL is empty, skipping webhook dispatch.");
            return;
        }

        try {
            String payload = buildDiscordWebhookPayload(title, generateReportText(topLimit), topLimit);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mint-NetworkAnalyser/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            LOGGER.info("[NetworkAnalyser] Report successfully sent to webhook.");
                        } else {
                            LOGGER.warn("[NetworkAnalyser] Webhook returned HTTP {}: {}", response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(throwable -> {
                        LOGGER.error("[NetworkAnalyser] Failed to send report to webhook", throwable);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("[NetworkAnalyser] Error preparing webhook request", e);
        }
    }

    public static void sendWebhookReportSync(String webhookUrl, String title, int topLimit) {
        if (webhookUrl == null || webhookUrl.isBlank() || isEmpty()) {
            return;
        }

        try {
            String payload = buildDiscordWebhookPayload(title, generateReportText(topLimit), topLimit);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mint-NetworkAnalyser/1.0")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.info("[NetworkAnalyser] Shutdown report successfully sent to webhook.");
            } else {
                LOGGER.warn("[NetworkAnalyser] Webhook returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOGGER.error("[NetworkAnalyser] Failed to send shutdown report to webhook: {}", e.getMessage());
        }
    }

    public static synchronized void startContinuousAnalysis(int intervalMinutes, String webhookUrl) {
        stopScheduledAnalysis();

        // Start tracking immediately on startup
        start();
        LOGGER.info("[NetworkAnalyser] Started network analyser on server startup.");

        if (intervalMinutes <= 0) {
            LOGGER.warn("[NetworkAnalyser] Invalid interval: {}. Periodic reporting disabled.", intervalMinutes);
            return;
        }

        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Mint-NetworkAnalyser-Scheduler");
            t.setDaemon(true);
            return t;
        });

        LOGGER.info("[NetworkAnalyser] Periodic reporting enabled. Reports will be sent to webhook every {} minute(s).", intervalMinutes);

        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                LOGGER.info("[NetworkAnalyser] Generating periodic network report (last {} minutes)...", intervalMinutes);
                sendWebhookReport(webhookUrl, "📊 Mint Network Analyser - Hourly Report", 10);
                reset();
            } catch (Exception e) {
                LOGGER.error("[NetworkAnalyser] Error during periodic network analysis reporting cycle", e);
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public static void handleShutdown() {
        try {
            if (running) {
                stop();
                if (dev.bacteriawa.mint.config.modules.misc.NetworkAnalyserConfig.sendOnShutdown
                        && !dev.bacteriawa.mint.config.modules.misc.NetworkAnalyserConfig.webhookUrl.isBlank()
                        && !isEmpty()) {
                    LOGGER.info("[NetworkAnalyser] Sending shutdown network report to webhook...");
                    sendWebhookReportSync(dev.bacteriawa.mint.config.modules.misc.NetworkAnalyserConfig.webhookUrl,
                            "🛑 Mint Network Analyser - Server Shutdown Report", 10);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[NetworkAnalyser] Error during shutdown report dispatch", e);
        } finally {
            stopScheduledAnalysis();
        }
    }

    public static synchronized void stopScheduledAnalysis() {
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdownNow();
            scheduledExecutor = null;
        }
    }
}
