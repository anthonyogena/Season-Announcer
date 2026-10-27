package com.neuralknights;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.awt.Color;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SeasonAnnouncerBot extends ListenerAdapter {

    // === ENVIRONMENT CONFIGURATION ===
    private static String BOT_TOKEN;
    private static String ANNOUNCEMENT_CHANNEL_ID;
    private static String SEASON_IMAGE_URL;
    private static ZoneId TIME_ZONE;

    // === SCHEDULE SETTINGS ===
    private static final int SEASON_LENGTH_WEEKS = 1;
    private static final int OFF_SEASON_WEEKS   = 2;
    private static final int CYCLE_WEEKS        = SEASON_LENGTH_WEEKS + OFF_SEASON_WEEKS;

    // Permanent Cycle Anchor Date (Year, Month, Day, Hour, Minute, Second, Nano, Zone)
    // Customize this date to match your first season launch anchor point!
    private static ZonedDateTime ANCHOR_DATE;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "season-announcer");
                t.setDaemon(false);
                return t;
            });

    private JDA jda;

    public static void main(String[] args) throws InterruptedException {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        // Load configuration variables from .env or OS Environment
        BOT_TOKEN = getEnv(dotenv, "DISCORD_TOKEN");
        ANNOUNCEMENT_CHANNEL_ID = getEnv(dotenv, "ANNOUNCEMENT_CHANNEL_ID");
        SEASON_IMAGE_URL = getEnvOrDefault(dotenv, "SEASON_IMAGE_URL", "https://i.imgur.com/gqmp5WA.jpeg");

        String tzString = getEnvOrDefault(dotenv, "TIMEZONE", "Asia/Manila");
        TIME_ZONE = ZoneId.of(tzString);

        // Anchor set to Nov 1, 2026, 15:00 Manila Time
        ANCHOR_DATE = ZonedDateTime.of(2026, 11, 1, 15, 0, 0, 0, TIME_ZONE);

        new SeasonAnnouncerBot().start();
    }

    private static String getEnv(Dotenv dotenv, String key) {
        String val = dotenv.get(key);
        if (val == null || val.isEmpty()) {
            val = System.getenv(key);
        }
        return val;
    }

    private static String getEnvOrDefault(Dotenv dotenv, String key, String defaultValue) {
        String val = getEnv(dotenv, key);
        return (val == null || val.isEmpty()) ? defaultValue : val;
    }

    public void start() throws InterruptedException {
        if (BOT_TOKEN == null || BOT_TOKEN.isEmpty()) {
            System.err.println("FATAL ERROR: DISCORD_TOKEN environment variable is missing!");
            System.exit(1);
        }
        if (ANNOUNCEMENT_CHANNEL_ID == null || ANNOUNCEMENT_CHANNEL_ID.isEmpty()) {
            System.err.println("FATAL ERROR: ANNOUNCEMENT_CHANNEL_ID environment variable is missing!");
            System.exit(1);
        }

        jda = JDABuilder.createDefault(BOT_TOKEN)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build();

        jda.awaitReady();
        System.out.println("Bot is online.");

        // Clear global commands to prevent UI duplicates
        jda.updateCommands().queue();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            jda.shutdown();
        }));
    }

    @Override
    public void onReady(ReadyEvent event) {
        GuildMessageChannel channel = event.getJDA().getChannelById(GuildMessageChannel.class, ANNOUNCEMENT_CHANNEL_ID);
        if (channel == null) {
            System.err.println("Error: announcement channel not found (" + ANNOUNCEMENT_CHANNEL_ID + "). Check View Channel permissions.");
            return;
        }

        // Register guild-specific slash commands instantly
        Guild guild = channel.getGuild();
        guild.updateCommands().addCommands(
                Commands.slash("announce", "Manually trigger the season start announcement"),
                Commands.slash("endseason", "Manually trigger the season end announcement"),
                Commands.slash("status", "Check current season status and next scheduled date"),
                Commands.slash("schedule", "Check current season schedule")
        ).queue(
                ok -> System.out.println("✅ Guild Slash Commands updated instantly for: " + guild.getName()),
                err -> System.err.println("❌ Failed to update Guild Slash Commands: " + err.getMessage())
        );

        calculateAndSchedule(channel);
    }

    private void calculateAndSchedule(GuildMessageChannel channel) {
        ZonedDateTime now = ZonedDateTime.now(TIME_ZONE);

        ZonedDateTime nextStart = ANCHOR_DATE;
        while (nextStart.plusWeeks(CYCLE_WEEKS).isBefore(now)) {
            nextStart = nextStart.plusWeeks(CYCLE_WEEKS);
        }

        if (nextStart.isBefore(now)) {
            ZonedDateTime currentSeasonEnd = nextStart.plusWeeks(SEASON_LENGTH_WEEKS);
            if (now.isBefore(currentSeasonEnd)) {
                scheduleEndAnnouncement(channel, currentSeasonEnd, TIME_ZONE);
                nextStart = nextStart.plusWeeks(CYCLE_WEEKS);
            } else {
                nextStart = nextStart.plusWeeks(CYCLE_WEEKS);
            }
        }

        scheduleNextStartAnnouncement(channel, nextStart, TIME_ZONE);
    }

    private void scheduleNextStartAnnouncement(GuildMessageChannel channel, ZonedDateTime nextStart, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        long delay = Duration.between(now, nextStart).getSeconds();

        if (delay < 0) delay = 5;

        final ZonedDateTime finalStart = nextStart;
        final ZonedDateTime finalEnd = finalStart.plusWeeks(SEASON_LENGTH_WEEKS);

        System.out.println("Next season start scheduled for: " + finalStart);
        System.out.println("Season end scheduled for:        " + finalEnd);

        scheduler.schedule(() -> {
            try {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Funsmoke Time");
                embed.setDescription("Get ready for another week of suffering");
                embed.setImage(SEASON_IMAGE_URL);
                embed.setColor(Color.CYAN);

                channel.sendMessage("@everyone")
                        .setEmbeds(embed.build())
                        .setAllowedMentions(Collections.singletonList(Message.MentionType.EVERYONE))
                        .queue(
                                ok  -> System.out.println("[OK]   Season announced: " + finalStart),
                                err -> System.err.println("[FAIL] " + err.getMessage())
                        );
            } catch (Exception e) {
                System.err.println("[ERROR] " + e);
            } finally {
                scheduleEndAnnouncement(channel, finalEnd, zone);
                scheduleNextStartAnnouncement(channel, finalStart.plusWeeks(CYCLE_WEEKS), zone);
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void scheduleEndAnnouncement(GuildMessageChannel channel, ZonedDateTime endTime, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        long endDelay = Duration.between(now, endTime).getSeconds();

        if (endDelay > 0) {
            System.out.println("Active season end scheduled for: " + endTime);
            scheduler.schedule(() -> {
                try {
                    long nextStartEpoch = endTime.plusWeeks(OFF_SEASON_WEEKS).toEpochSecond();
                    channel.sendMessage("🏁 **Season has ended.** Next Funsmoke starts <t:" + nextStartEpoch + ":R>.").queue();
                } catch (Exception e) {
                    System.err.println("[ERROR] end announce: " + e);
                }
            }, endDelay, TimeUnit.SECONDS);
        }
    }

    // === SLASH COMMAND HANDLER ===
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getChannel().getId().equals(ANNOUNCEMENT_CHANNEL_ID)) {
            event.reply("This command can only be used in the announcements channel.").setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "announce" -> {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Funsmoke Time");
                embed.setDescription("Get ready for another week of suffering");
                embed.setImage(SEASON_IMAGE_URL);
                embed.setColor(Color.CYAN);

                event.getChannel().sendMessage("@everyone")
                        .setEmbeds(embed.build())
                        .setAllowedMentions(Collections.singletonList(Message.MentionType.EVERYONE))
                        .queue();

                event.reply("Announcement sent!").setEphemeral(true).queue();
            }
            case "endseason" -> {
                ZonedDateTime now = ZonedDateTime.now(TIME_ZONE);
                ZonedDateTime start = ANCHOR_DATE;
                while (start.plusWeeks(CYCLE_WEEKS).isBefore(now)) {
                    start = start.plusWeeks(CYCLE_WEEKS);
                }
                long nextStartEpoch = start.isBefore(now) ? start.plusWeeks(CYCLE_WEEKS).toEpochSecond() : start.toEpochSecond();

                event.getChannel().sendMessage("🏁 **Season has ended.** Next Funsmoke starts <t:" + nextStartEpoch + ":R>.").queue();
                event.reply("End season announcement sent!").setEphemeral(true).queue();
            }
            case "status", "schedule" -> {
                ZonedDateTime now = ZonedDateTime.now(TIME_ZONE);
                ZonedDateTime start = ANCHOR_DATE;
                while (start.plusWeeks(CYCLE_WEEKS).isBefore(now)) {
                    start = start.plusWeeks(CYCLE_WEEKS);
                }
                if (start.isBefore(now)) {
                    ZonedDateTime end = start.plusWeeks(SEASON_LENGTH_WEEKS);
                    if (now.isBefore(end)) {
                        long endEpoch = end.toEpochSecond();
                        event.reply("🟢 **Season is currently ACTIVE!**\nEnds: <t:" + endEpoch + ":F> (<t:" + endEpoch + ":R>)").queue();
                        return;
                    }
                    start = start.plusWeeks(CYCLE_WEEKS);
                }

                long startEpoch = start.toEpochSecond();
                event.reply("🔴 **Off-Season.**\nNext Season Starts: <t:" + startEpoch + ":F> (<t:" + startEpoch + ":R>)").queue();
            }
        }
    }

    // === PREFIX COMMAND HANDLER (!announce) ===
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.getChannel().getId().equals(ANNOUNCEMENT_CHANNEL_ID)) return;

        String msg = event.getMessage().getContentRaw().trim();

        if (msg.equalsIgnoreCase("!announce")) {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Funsmoke Time");
            embed.setDescription("Get ready for another week of suffering");
            embed.setImage(SEASON_IMAGE_URL);
            embed.setColor(Color.CYAN);

            event.getChannel().sendMessage("@everyone")
                    .setEmbeds(embed.build())
                    .setAllowedMentions(Collections.singletonList(Message.MentionType.EVERYONE))
                    .queue();

            event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")).queue();
        }
        else if (msg.equalsIgnoreCase("!endseason")) {
            ZonedDateTime now = ZonedDateTime.now(TIME_ZONE);
            ZonedDateTime start = ANCHOR_DATE;
            while (start.plusWeeks(CYCLE_WEEKS).isBefore(now)) {
                start = start.plusWeeks(CYCLE_WEEKS);
            }
            long nextStartEpoch = start.isBefore(now) ? start.plusWeeks(CYCLE_WEEKS).toEpochSecond() : start.toEpochSecond();

            event.getChannel().sendMessage("🏁 **Season has ended.** Next Funsmoke starts <t:" + nextStartEpoch + ":R>.").queue();
            event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")).queue();
        }
        else if (msg.equalsIgnoreCase("!schedule") || msg.equalsIgnoreCase("!status")) {
            ZonedDateTime now = ZonedDateTime.now(TIME_ZONE);

            ZonedDateTime start = ANCHOR_DATE;
            while (start.plusWeeks(CYCLE_WEEKS).isBefore(now)) {
                start = start.plusWeeks(CYCLE_WEEKS);
            }
            if (start.isBefore(now)) {
                ZonedDateTime end = start.plusWeeks(SEASON_LENGTH_WEEKS);
                if (now.isBefore(end)) {
                    long endEpoch = end.toEpochSecond();
                    event.getChannel().sendMessage("🟢 **Season is currently ACTIVE!**\nEnds: <t:" + endEpoch + ":F> (<t:" + endEpoch + ":R>)").queue();
                    return;
                }
                start = start.plusWeeks(CYCLE_WEEKS);
            }

            long startEpoch = start.toEpochSecond();
            event.getChannel().sendMessage("🔴 **Off-Season.**\nNext Season Starts: <t:" + startEpoch + ":F> (<t:" + startEpoch + ":R>)").queue();
        }
    }
}