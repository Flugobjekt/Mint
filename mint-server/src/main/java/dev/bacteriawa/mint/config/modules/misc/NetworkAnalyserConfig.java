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
    public static boolean scheduledAnalysisEnabled = true;

    @Configuration
    public static int scheduledIntervalMinutes = 60;

    @Configuration
    public static int scheduledDurationMinutes = 10;

    @Configuration
    public static String webhookUrl = "";

    public static void loaded(CommentedFileConfig config) {
        if (networkAnalyserEnabled) {
            Bukkit.getCommandMap().register("networkanalyser", "mint", new NetworkAnalyserCommand());

            if (scheduledAnalysisEnabled) {
                NetworkAnalyser.startScheduledAnalysis(scheduledIntervalMinutes, scheduledDurationMinutes, webhookUrl);
            }
        } else {
            NetworkAnalyser.stopScheduledAnalysis();
        }
    }
}

