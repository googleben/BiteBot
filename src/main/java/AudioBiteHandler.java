import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventListener;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.audio.AudioEncodingType;
import sx.blah.discord.handle.audio.IAudioProvider;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.obj.Channel;
import sx.blah.discord.handle.impl.obj.Guild;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.handle.obj.IVoiceState;
import sx.blah.discord.util.MissingPermissionsException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class AudioBiteHandler {
    
    static Logger log = LoggerFactory.getLogger(AudioBiteHandler.class);
    
    public HashMap<String, AudioTrack> bites;
    
    public Set<IGuild> playingIn;
    
    public HashMap<String, Long> userMap;
    
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public List<String> biteNames;
    
    public AudioBiteHandler() {
        AudioSourceManagers.registerLocalSource(BenBot.audioPlayerManager);
        registerFiles();
        playingIn = new HashSet<>();
        try {
            userMap = gson.fromJson(new FileReader("./data/bites.json"), new TypeToken<HashMap<String,Long>>(){}.getType());
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (userMap==null) userMap = new HashMap<>();
    }
    
    int tries = 0;
    
    public void registerFiles() {
        bites = new HashMap<>();
        System.out.println("a");
        System.gc();
        System.out.println("b");
        normalize();
        System.out.println("c");
        Path biteLoc = Paths.get("./bites");
        biteNames = new ArrayList<>();
        try {
            biteNames = Files.list(biteLoc).filter(p -> p.toString().endsWith(".mp3") || p.toString().endsWith(".wav"))
                    .map(p -> {
                       String ans = p.getFileName().toString();
                       return ans.substring(0, ans.length()-4);
                    }).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Files.list(biteLoc).filter(p -> p.toString().endsWith(".mp3") || p.toString().endsWith(".wav")).forEach(p -> BenBot.audioPlayerManager.loadItem(p.toString(), new AudioLoadHandler()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (bites.keySet().containsAll(biteNames) || tries++>10) {
            tries = 0;
            try {
                Files.write(Paths.get("./data/bites.json"), gson.toJson(userMap).getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else registerFiles();
        
    }
    
    private void normalize() {
        /*try {
            for (int i = 0; i<3; i++) {
                System.out.println("d");
                Process p = Runtime.getRuntime().exec("cmd.exe /C normalize.bat");
                p.waitFor();
                //IOUtils.copy(p.getInputStream(), System.out);
                //IOUtils.copy(p.getErrorStream(), System.out);
                InputStream s = p.getInputStream();
                byte[] buffer = new byte[1024];
                int size;
                while ((size = s.read()) != -1) System.out.write(buffer, 0, size);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }
    
    @EventSubscriber
    public void onMesageReveivedEvent(MessageReceivedEvent event) {
        String message = event.getMessage().getContent();
        if (!message.startsWith("!")) return;
        String name = message.substring(1);
        if (bites.containsKey(name)) {
            try {
                event.getMessage().delete();
            } catch (MissingPermissionsException e) {
                log.debug("Insufficient permissions to delete message");
            }
            IGuild guild = event.getGuild();
            if (playingIn.contains(guild)) return;
            IVoiceChannel channel = event.getAuthor().getVoiceStateForGuild(guild).getChannel();
            playAudio(name, guild, channel);
        }
    }
    
    public void playAudio(String name, IGuild guild, IVoiceChannel channel) {
        if (channel.isConnected()) channel.leave();
        channel.join();
        AudioPlayer player = BenBot.audioPlayerManager.createPlayer();
        AudioSender sender = new AudioSender(player);
        player.addListener(p -> {
            if (p instanceof TrackEndEvent) {
                p.player.destroy();
                sender.done = true;
                channel.leave();
                playingIn.remove(guild);
            }
        });
        guild.getAudioManager().setAudioProvider(sender);
        bites.put(name, bites.get(name).makeClone());
        player.playTrack(bites.get(name));
    }
    
    public void playAudio(String name, IUser user) {
        for (IGuild guild : BenBot.client.getGuilds()) {
            if (user.getVoiceStateForGuild(guild).getChannel()!=null) {
                playAudio(name, guild, user.getVoiceStateForGuild(guild).getChannel());
            }
        }
    }
    
    public void playAudio(String name) {
        playAudio(name, BenBot.client.getUserByID(66312966248607744L));
    }
    
    class AudioLoadHandler implements AudioLoadResultHandler {
        
        public void trackLoaded(AudioTrack track) {
            String[] arr = track.getIdentifier().split("\\\\");
            String name = arr[arr.length-1].substring(0, arr[arr.length-1].length()-4).trim();
            bites.put(name, track);
            log.debug("Loaded "+track.getIdentifier()+" ("+name+")");
        }
    
        public void playlistLoaded(AudioPlaylist playlist) {
        
        }
    
        public void noMatches() {
            log.warn("No match while attempting to load audio");
        }
    
        public void loadFailed(FriendlyException e) {
        
        }
    }
    
    class AudioSender implements IAudioProvider {
        private AudioPlayer player;
        private AudioFrame lastFrame;
        boolean done = false;
        public AudioSender(AudioPlayer player) {
            this.player = player;
        }
    
        @Override
        public boolean isReady() {
            if (done) return true;
            lastFrame = player.provide();
            return lastFrame!=null;
        }
    
        @Override
        public byte[] provide() {
            return lastFrame.data;
        }
        
        @Override
        public AudioEncodingType getAudioEncodingType() {
            return AudioEncodingType.OPUS;
        }
    }
    
}
