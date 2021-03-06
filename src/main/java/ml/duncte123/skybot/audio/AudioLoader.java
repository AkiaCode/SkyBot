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

package ml.duncte123.skybot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import ml.duncte123.skybot.CommandManager;
import ml.duncte123.skybot.audio.sourcemanagers.youtube.YoutubeAudioSourceManagerOverride;
import ml.duncte123.skybot.commands.music.RadioCommand;
import ml.duncte123.skybot.exceptions.LimitReachedException;
import ml.duncte123.skybot.extensions.AudioTrackKt;
import ml.duncte123.skybot.extensions.StringKt;
import ml.duncte123.skybot.objects.RadioStream;
import ml.duncte123.skybot.objects.TrackUserData;
import ml.duncte123.skybot.objects.command.CommandContext;
import ml.duncte123.skybot.utils.AudioUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static me.duncte123.botcommons.messaging.EmbedUtils.embedField;
import static me.duncte123.botcommons.messaging.MessageUtils.sendEmbed;
import static me.duncte123.botcommons.messaging.MessageUtils.sendMsg;

public class AudioLoader implements AudioLoadResultHandler {

    private final CommandContext ctx;
    private final long requester;
    private final GuildMusicManager mng;
    private final boolean announce;
    private final String trackUrl;
    private final boolean isPatron;

    public AudioLoader(CommandContext ctx, GuildMusicManager mng, boolean announce, String trackUrl, boolean isPatron) {
        this.ctx = ctx;
        this.requester = ctx.getAuthor().getIdLong();
        this.mng = mng;
        this.announce = announce;
        this.trackUrl = trackUrl;
        this.isPatron = isPatron;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        addToIndex(track);

        track.setUserData(new TrackUserData(this.requester));

        final TrackScheduler scheduler = this.mng.getScheduler();

        if (!scheduler.canQueue()) {
            sendMsg(this.ctx, String.format("Could not queue track because limit of %d tracks has been reached", TrackScheduler.MAX_QUEUE_SIZE));
            return;
        }

        try {
            scheduler.addToQueue(track, this.isPatron);

            if (this.announce) {
                final AudioTrackInfo info = track.getInfo();
                final String title = getSteamTitle(track, info.title, this.ctx.getCommandManager());
                final String msg = "Adding to queue: [" + StringKt.abbreviate(title, 500) + "](" + info.uri + ')';

                sendEmbed(this.ctx,
                    embedField(AudioUtils.EMBED_TITLE, msg)
                        .setThumbnail(AudioTrackKt.getImageUrl(track, true))
                );
            }
        }
        catch (LimitReachedException e) {
            sendMsg(this.ctx, String.format("You exceeded the maximum queue size of %s tracks", e.getSize()));
        }
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        if (playlist.getTracks().isEmpty()) {
            sendEmbed(this.ctx, embedField(AudioUtils.EMBED_TITLE, "Error: This playlist is empty."));

            return;
        }

        final TrackUserData userData = new TrackUserData(this.requester);
        final List<AudioTrack> tracks = playlist.getTracks().stream().peek((track) -> {
            addToIndex(track);
            track.setUserData(userData);
        })
            .collect(Collectors.toList());

        try {
            final TrackScheduler trackScheduler = this.mng.getScheduler();

            for (final AudioTrack track : tracks) {
                trackScheduler.addToQueue(track, this.isPatron);
            }

            if (this.announce) {
                final String sizeMsg;

                if (playlist instanceof BigChungusPlaylist && ((BigChungusPlaylist) playlist).isBig()) {
                    sizeMsg = playlist.getTracks().size() + "/" + ((BigChungusPlaylist) playlist).getOriginalSize();
                } else {
                    sizeMsg = String.valueOf(playlist.getTracks().size());
                }

                final String msg = String.format(
                    "Adding **%s** tracks to the queue from playlist %s",
                    sizeMsg,
                    playlist.getName()
                );

                sendEmbed(this.ctx, embedField(AudioUtils.EMBED_TITLE, msg));
            }
        }
        catch (LimitReachedException e) {
            if (this.announce) {
                sendMsg(this.ctx, String.format("The first %s tracks have been queued up", e.getSize()));
            }
        }

    }

    @Override
    public void noMatches() {
        if (this.announce) {
            sendEmbed(this.ctx, embedField(AudioUtils.EMBED_TITLE, "Nothing found by *" + StringKt.abbreviate(this.trackUrl, MessageEmbed.VALUE_MAX_LENGTH) + '*'));
        }
    }

    @Override
    public void loadFailed(FriendlyException exception) {

        if (exception.getCause() != null && exception.getCause() instanceof LimitReachedException) {
            final LimitReachedException cause = (LimitReachedException) exception.getCause();
            sendMsg(this.ctx, String.format("%s, maximum of %d tracks exceeded", cause.getMessage(), cause.getSize()));

            return;
        }

        if (!this.announce) {
            return;
        }

        if (exception.getMessage().endsWith("Playback on other websites has been disabled by the video owner.")) {
            sendEmbed(this.ctx, embedField(AudioUtils.EMBED_TITLE, "Could not play: " + this.trackUrl
                + "\nExternal playback of this video was blocked by YouTube."));
            return;
        }

        @Nullable Throwable root = ExceptionUtils.getRootCause(exception);

        if (root == null) {
            root = exception;
        }

        sendEmbed(this.ctx, embedField(AudioUtils.EMBED_TITLE, "Could not play: " + StringKt.abbreviate(root.getMessage(), MessageEmbed.VALUE_MAX_LENGTH)
            + "\nIf this happens often try another link or join our [support guild](https://dunctebot.link/server) for more!"));

    }

    private void addToIndex(AudioTrack track) {
        if (!(track instanceof YoutubeAudioSourceManagerOverride.DoNotCache)) {
            this.ctx.getYoutubeCache().addToIndex(track);
        }
    }

    private static String getSteamTitle(AudioTrack track, String rawTitle, CommandManager commandManager) {
        String title = rawTitle;

        if (track.getInfo().isStream) {
            final Optional<RadioStream> stream = ((RadioCommand) commandManager.getCommand("radio"))
                .getRadioStreams().stream().filter(s -> s.getUrl().equals(track.getInfo().uri)).findFirst();

            if (stream.isPresent()) {
                title = stream.get().getName();
            }
        }

        return title;
    }
}
