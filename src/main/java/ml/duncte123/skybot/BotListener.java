package ml.duncte123.skybot;

import ml.duncte123.skybot.utils.*;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.utils.PermissionUtil;
import org.apache.commons.lang3.time.DateUtils;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class BotListener extends ListenerAdapter {

    /**
     * This is our 'custom' swearword filter
     */
    private final BadWordFilter filter = new BadWordFilter();
    /**
     * This is the command parser
     */
    private static CommandParser parser = new CommandParser();
    /**
     * When a command gets ran, it'll be stored in here
     */
    private static HashMap<Guild, TextChannel> lastGuildChannel = new HashMap<>();


    /**
     * This timer is responsible for our status updates
     */
    private Timer timer = new Timer();
    /**
     * This timer is for checking unbans
     */
    private Timer unbanTimer = new Timer();

    /**
     * Listen for messages send to the bot
     * @param event The corresponding {@link net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent GuildMessageReceivedEvent}
     */
    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event){

        if(event.getMessage().getContent().equals(Config.prefix + "shutdown") && event.getAuthor().getId().equals(Config.ownerId)){
            AirUtils.log(CustomLog.Level.INFO,"Shutting down!!!");
            timer.cancel();
            unbanTimer.cancel();
            event.getJDA().shutdown();
            try {
                AirUtils.db.getConnection().close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        if(event.getAuthor().isFake() || event.getAuthor().isBot() || event.getMember()==null){
            return;
        }

        Permission[] adminPerms = {
                Permission.MESSAGE_MANAGE
        };
        if(PermissionUtil.checkPermission(event.getChannel(), event.getGuild().getSelfMember(), Permission.MESSAGE_MANAGE) && !(AirUtils.blackList.contains(event.getGuild().getId()))) { //Bot has no perms :(
            if (!PermissionUtil.checkPermission(event.getMember(), adminPerms)) {
                Message messageToCheck = event.getMessage();
                if (filter.filterText(messageToCheck.getRawContent())) {
                    messageToCheck.delete().reason("Blocked for bad swearing: " + messageToCheck.getContent()).queue();
                    event.getChannel().sendMessage("Hello there, " + event.getAuthor().getAsMention() + " please do not use cursive language within this Discord.").queue(
                            m -> m.delete().queueAfter(10, TimeUnit.SECONDS));
                    return;
                }
            }
        }

        if(event.getMessage().getContent().startsWith(Config.prefix) && event.getMessage().getAuthor().getId() != event.getJDA().getSelfUser().getId()){
            // run the a command
            lastGuildChannel.put(event.getGuild(), event.getChannel());
            SkyBot.handleCommand(parser.parse(event.getMessage().getRawContent(), event));
            return;
        }


        if(event.getMessage().getMentionedUsers().contains(event.getJDA().getSelfUser()) && event.getChannel().canTalk()) {
            if(event.getMessage().getRawContent().split(" ").length > 1){
                lastGuildChannel.put(event.getGuild(), event.getChannel());
                SkyBot.handleCommand(parser.parse(event.getMessage().getRawContent().replaceFirst("<@" + event.getJDA().getSelfUser().getId() + "> ", Config.prefix), event));
                return;
            }
            event.getChannel().sendMessage("Hey <@" + event.getAuthor().getId() + ">, try `" + Config.prefix + "help` for a list of commands. If it doesn't work scream at _duncte123#1245_").queue();
        }

    }

    /**
     * When the bot is ready to go
     * @param event The corresponding {@link net.dv8tion.jda.core.events.ReadyEvent ReadyEvent}
     */
    @Override
    public void onReady(ReadyEvent event){
        AirUtils.log(CustomLog.Level.INFO, "Logged in as " + String.format("%#s", event.getJDA().getSelfUser()));
        //event.getJDA().getGuilds().get(0).getDefaultChannel().sendMessage(Main.defaultName+" V" + Config.version +" has been restarted.").queue();
        TimerTask myTask = new TimerTask() {
            @Override
            public void run() {
                SkyBot.updateStatus();
            }
        };
        timer.schedule(myTask, DateUtils.MILLIS_PER_MINUTE, DateUtils.MILLIS_PER_MINUTE);

        TimerTask unbanTask = new TimerTask() {
            @Override
            public void run() {
                AirUtils.checkUnbans();
            }
        };
        unbanTimer.schedule(unbanTask, DateUtils.MILLIS_PER_MINUTE*10, DateUtils.MILLIS_PER_MINUTE*10);

    }

    /**
     * This will fire when a new member joins, if the guild is not on the {@link ml.duncte123.skybot.utils.AirUtils#blackList blackList} we will send a message
     * @param event The corresponding {@link net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent GuildMemberJoinEvent}
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event){

        if (AirUtils.guildSettings.get(event.getGuild().getId()).isEnableJoinMessage()) {
            TextChannel publicChannel = AirUtils.getPublicChannel(event.getGuild());
            String msg = "Welcome " + event.getMember().getAsMention() + ", to the official " + event.getGuild().getName() + " guild.";
            publicChannel.sendMessage(msg).queue();
        }
    }

    /**
     * This will fire when the bot joins a guild and we check if we are allowed to join this guild
     * @param event The corresponding {@link net.dv8tion.jda.core.events.guild.GuildJoinEvent GuildJoinEvent}
     */
    @Override
    public void onGuildJoin(GuildJoinEvent event) {

        /*AirUtils.log("DuncteBotGuildJoin", CustomLog.Level.INFO, "Joining guild: " + event.getGuild().getName() + ". " +
                (AirUtils.whiteList.contains(event.getGuild().getId()) ? "Guild is on whitelist." : "Guild is not on whitelist, leaving."));

        if (AirUtils.whiteList.contains(event.getGuild().getId())) return;

        AirUtils.getPublicChannel(event.getGuild()).sendMessage("Hey " + event.getGuild().getOwner().getAsMention()
                + ", I'm not made to be in this guild and will leave it in 20 seconds")
                .queue(
                    (m) -> event.getGuild().leave().queueAfter(20, TimeUnit.SECONDS)
        );*/
        AirUtils.log("DuncteBotGuildJoin", CustomLog.Level.INFO, "Joining guild: " + event.getGuild().getName() + ".");
        AirUtils.registerNewGuild(event.getGuild().getId());
    }

    /**
     * This will fire when a member leaves a channel in a guild, we check if the channel is empty and if it is we leave it
     * @param event {@link net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent GuildVoiceLeaveEvent}
     */
    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event){
        if(!event.getVoiceState().getMember().getUser().getId().equals(event.getJDA().getSelfUser().getId()) && event.getGuild().getAudioManager().isConnected()){
            if (!event.getChannelLeft().getId().equals(event.getGuild().getAudioManager().getConnectedChannel().getId())) { return; }
            if(event.getChannelLeft().getMembers().size() <= 1){
                SkyBot.au.getMusicManager(event.getGuild()).player.stopTrack();
                SkyBot.au.getMusicManager(event.getGuild()).player.setPaused(false);
                SkyBot.au.getMusicManager(event.getGuild()).scheduler.queue.clear();
                lastGuildChannel.get(event.getGuild()).sendMessage(AirUtils.embedMessage("Leaving voice channel because all the members have left it.")).queue();
                if(event.getGuild().getAudioManager().isConnected()){
                    event.getGuild().getAudioManager().closeAudioConnection();
                    event.getGuild().getAudioManager().setSendingHandler(null);
                }
            }
        }
    }

    /**
     * This will fire when a member moves from channel, if a member moves we will check if our channel is empty
     * @param event {@link net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent GuildVoiceMoveEvent}
     */
    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
        if(!event.getVoiceState().getMember().getUser().getId().equals(event.getJDA().getSelfUser().getId()) && event.getGuild().getAudioManager().isConnected()) {

            if(event.getChannelLeft()!=null) {
                if (!event.getChannelLeft().getId().equals(event.getGuild().getAudioManager().getConnectedChannel().getId())) { return; }
                if(event.getChannelLeft().getMembers().size() <= 1){
                    SkyBot.au.getMusicManager(event.getGuild()).player.stopTrack();
                    SkyBot.au.getMusicManager(event.getGuild()).player.setPaused(false);
                    SkyBot.au.getMusicManager(event.getGuild()).scheduler.queue.clear();
                    lastGuildChannel.get(event.getGuild()).sendMessage(AirUtils.embedMessage("Leaving voice channel because all the members have left it.")).queue();
                    if(event.getGuild().getAudioManager().isConnected()){
                        event.getGuild().getAudioManager().closeAudioConnection();
                        event.getGuild().getAudioManager().setSendingHandler(null);
                    }
                }
            }

            if(event.getChannelJoined()!=null) {
                if (!event.getChannelJoined().getId().equals(event.getGuild().getAudioManager().getConnectedChannel().getId())) { return; }
                if(event.getChannelJoined().getMembers().size() <= 1){
                    SkyBot.au.getMusicManager(event.getGuild()).player.stopTrack();
                    SkyBot.au.getMusicManager(event.getGuild()).player.setPaused(false);
                    SkyBot.au.getMusicManager(event.getGuild()).scheduler.queue.clear();
                    lastGuildChannel.get(event.getGuild()).sendMessage(AirUtils.embedMessage("Leaving voice channel because all the members have left it.")).queue();
                    if(event.getGuild().getAudioManager().isConnected()){
                        event.getGuild().getAudioManager().setSendingHandler(null);
                        event.getGuild().getAudioManager().closeAudioConnection();
                    }
                }
            }
        }
    }

}
