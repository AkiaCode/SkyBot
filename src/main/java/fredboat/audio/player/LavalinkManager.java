/*
 * Skybot, a multipurpose discord bot
 *      Copyright (C) 2017 - 2020  Duncan "duncte123" Sterken & Ramid "ramidzkh" Khan & Maurice R S "Sanduhr32"
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fredboat.audio.player;

import lavalink.client.io.LavalinkRegion;
import lavalink.client.io.Link;
import lavalink.client.io.jda.JdaLavalink;
import lavalink.client.player.IPlayer;
import lavalink.client.player.LavaplayerPlayerWrapper;
import ml.duncte123.skybot.SkyBot;
import ml.duncte123.skybot.objects.config.DunctebotConfig;
import ml.duncte123.skybot.utils.AudioUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import javax.annotation.Nonnull;
import java.net.URI;
import java.util.Base64;


/**
 * This class has been taken from
 * https://github.com/Frederikam/FredBoat/blob/master/FredBoat/src/main/java/fredboat/audio/player/LavalinkManager.java\
 * and has been modified to fit my needs
 */
public final class LavalinkManager {

    public static final LavalinkManager INS = new LavalinkManager();
    private JdaLavalink lavalink = null;
    private DunctebotConfig config = null;
    private AudioUtils audioUtils = null;

    private LavalinkManager() {
    }

    public void start(DunctebotConfig config, AudioUtils audioUtils) {
        this.config = config;
        this.audioUtils = audioUtils;
        if (!isEnabled()) {
            return;
        }

        final String userId = getIdFromToken(this.config.discord.token);

        lavalink = new JdaLavalink(
            userId,
            this.config.discord.totalShards,
            shardId -> SkyBot.getInstance().getShardManager().getShardById(shardId)
        );

        loadNodes();
    }

    public boolean isEnabled() {
        return config.lavalink.enable;
    }

    public IPlayer createPlayer(long guildId) {
        return isEnabled()
            ? lavalink.getLink(String.valueOf(guildId)).getPlayer()
            : new LavaplayerPlayerWrapper(audioUtils.getPlayerManager().createPlayer());
    }

    public void openConnection(VoiceChannel channel) {
        final AudioManager audioManager = channel.getGuild().getAudioManager();

        // Turn on the deafen icon for the bot
        audioManager.setSelfDeafened(true);

        if (isEnabled()) {
            lavalink.getLink(channel.getGuild()).connect(channel);
        } else {
            audioManager.openAudioConnection(channel);
        }
    }

    public void closeConnection(Guild guild) {
        if (isEnabled()) {
            lavalink.getLink(guild).disconnect();
        } else {
            guild.getAudioManager().closeAudioConnection();
        }
    }

    public boolean isConnected(Guild guild) {
        return isEnabled() ?
            lavalink.getLink(guild).getState() == Link.State.CONNECTED :
            guild.getAudioManager().isConnected();
    }

    public VoiceChannel getConnectedChannel(@Nonnull Guild guild) {
        // NOTE: never use the local audio manager, since the audio connection may be remote
        // there is also no reason to look the channel up remotely from lavalink, if we have access to a real guild
        // object here, since we can use the voice state of ourselves (and lavalink 1.x is buggy in keeping up with the
        // current voice channel if the bot is moved around in the client)
        return guild.getSelfMember().getVoiceState().getChannel();
    }

    public JdaLavalink getLavalink() {
        return lavalink;
    }

    private void loadNodes() {
        final JdaLavalink lavalink = getLavalink();

        for (final DunctebotConfig.Lavalink.LavalinkNode node : config.lavalink.nodes) {
            lavalink.addNode(URI.create(node.wsurl), node.pass, LavalinkRegion.valueOf(node.region));
        }

    }

    private String getIdFromToken(String token) {
        return new String(
            Base64.getDecoder().decode(
                token.split("\\.")[0]
            )
        );
    }
}
