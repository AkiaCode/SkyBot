package me.duncte123.skybot.commands.music;

import me.duncte123.skybot.Command;
import me.duncte123.skybot.SkyBot;
import me.duncte123.skybot.audio.GuildMusicManager;
import me.duncte123.skybot.utils.AudioUtils;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

public class RickRollCommand implements Command {

	@Override
	public boolean called(String[] args, MessageReceivedEvent event) {
		// TODO Auto-generated method stub
		return event.getGuild().getAudioManager().isConnected();
	}

	@Override
	public void action(String[] args, MessageReceivedEvent event) {
		AudioUtils au = SkyBot.au;
		GuildMusicManager mng = au.getMusicManager(event.getGuild());
		
		au.loadAndPlay(mng, event.getTextChannel(), "/root/Desktop/music/rick.mp3", false);

	}

	@Override
	public String help() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public void executed(boolean success, MessageReceivedEvent event) {
		// TODO Auto-generated method stub

	}

}
