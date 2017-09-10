import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
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
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.MissingPermissionsException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class AudioBiteHandler {
    
    static Logger log = LoggerFactory.getLogger(AudioBiteHandler.class);
    
    public HashMap<String, AudioTrack> bites;
    
    public Set<IGuild> playingIn;
    
    public HashMap<String, Long> userMap;
    
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public Set<String> biteNames;
    
    public AudioBiteHandler() {
        AudioSourceManagers.registerLocalSource(BenBot.instance.audioPlayerManager);
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
        try {
            registerFilesH();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void registerFilesH() throws IOException {
        normalize();
        bites = new HashMap<>();
        Path biteLoc = Paths.get("./bites");
        biteNames = Files.list(biteLoc).filter(p -> p.toString().endsWith(".mp3") || p.toString().endsWith(".wav"))
                .map(p -> {
                   String ans = p.getFileName().toString();
                   return ans.substring(0, ans.length()-4);
                }).collect(Collectors.toSet());
        Files.list(biteLoc).filter(p -> p.toString().endsWith(".mp3") || p.toString().endsWith(".wav")).forEach(this::load);
        Set<String> missing = missing();
        System.out.println(missing);
        if (missing.size()!=0 && tries++<10) {
            registerFilesH();
        }
        
    }
    
    private void load(Path p) {
        try {
            BenBot.instance.audioPlayerManager.loadItem(p.toString(), new AudioLoadHandler()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
    
    private Set<String> missing() {
        Set<String> curr = bites.keySet();
        return biteNames.stream().filter(s -> !curr.contains(s)).collect(Collectors.toSet());
    }
    
    public void normalize() {
        ProcessBuilder pb = new ProcessBuilder("normalize.exe", "bites/*.wav").redirectErrorStream(true);
        File dir = new File(".");
        pb.directory(dir);
        
        try {
            Process p = pb.start();
            StringWriter w = new StringWriter();
            new Thread(() -> {
                try {
                    IOUtils.copy(p.getInputStream(), w);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
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
        playingIn.add(guild);
        if (channel.isConnected()) channel.leave();
        channel.join();
        AudioPlayer player = BenBot.instance.audioPlayerManager.createPlayer();
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
        for (IGuild guild : BenBot.instance.client.getGuilds()) {
            if (user.getVoiceStateForGuild(guild).getChannel()!=null) {
                playAudio(name, guild, user.getVoiceStateForGuild(guild).getChannel());
            }
        }
    }
    
    public void playAudio(String name) {
        playAudio(name, BenBot.instance.client.getUserByID(66312966248607744L));
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
