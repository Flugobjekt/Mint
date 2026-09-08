package dev.bacteriawa.mint.config.modules.misc;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.bacteriawa.mint.commands.NetworkAnalyserCommand;
import dev.bacteriawa.mint.config.ConfigurationType;
import dev.bacteriawa.mint.config.annotation.Configuration;
import dev.bacteriawa.mint.config.annotation.Configurations;
import dev.bacteriawa.mint.functions.NetworkAnalyser;
import org.bukkit.Bukkit;

@Configurations(name = "networkanalyser", type = ConfigurationType.misc)
public class NetworkAnalyserConfig {
    @Configuration
    public static boolean networkAnalyserEnabled = true;

    @Configuration
    public static boolean startOnStartup = true;

    @Configuration
    public static int scheduledIntervalMinutes = 60;

    @Configuration
    public static String webhookUrl = "";

    @Configuration
    public static boolean sendOnShutdown = true;

    @Configuration
    public static boolean useWebhookEmbed = false;

    private static boolean shutdownHookRegistered = false;

    public static void loaded(CommentedFileConfig config) {
        if (networkAnalyserEnabled) {
            Bukkit.getCommandMap().register("networkanalyser", "mint", new NetworkAnalyserCommand());

            if (startOnStartup) {
                NetworkAnalyser.startContinuousAnalysis(scheduledIntervalMinutes, webhookUrl);
            }

            if (!shutdownHookRegistered) {
                shutdownHookRegistered = true;
                Runtime.getRuntime().addShutdownHook(new Thread(NetworkAnalyser::handleShutdown, "Mint-NetworkAnalyser-ShutdownHook"));
            }
        } else {
            NetworkAnalyser.stopScheduledAnalysis();
            NetworkAnalyser.stop();
        }
    }
}

