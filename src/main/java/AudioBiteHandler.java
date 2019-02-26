import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.VoiceChannel;
import discord4j.core.spec.VoiceChannelJoinSpec;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class AudioBiteHandler {
    
    static Logger log = LoggerFactory.getLogger(AudioBiteHandler.class);
    
    public HashMap<String, AudioTrack> bites;
    
    public Set<Guild> playingIn;
    
    public HashMap<String, Long> userMap;
    
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public Set<String> biteNames;
    
    public AudioBiteHandler(DiscordClient client) {
        client.getEventDispatcher().on(MessageCreateEvent.class).subscribe(this::onMesageReveivedEvent);
        AudioSourceManagers.registerLocalSource(BenBot.instance.audioPlayerManager);
        registerFiles();
        playingIn = new HashSet<>();
        try {
            userMap = gson.fromJson(new FileReader("./data/bites.json"), new TypeToken<HashMap<String,Long>>(){}.getType());
        } catch (Exception e) {
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
        ArrayList<String> arr = new ArrayList<>();
        BenBot.instance.commands = arr;
        Path biteLoc = Paths.get("./bites");
        biteNames = Files.list(biteLoc).filter(p -> p.toString().endsWith(".mp3") || p.toString().endsWith(".wav"))
                .map(p -> {
                   String ans = p.getFileName().toString();
                   return ans.substring(0, ans.length()-4);
                }).collect(Collectors.toSet());
        arr.addAll(biteNames);
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

    public void onMesageReveivedEvent(MessageCreateEvent event) {
        if (!event.getMessage().getContent().isPresent()) return;
        String message = event.getMessage().getContent().get();
        if (!message.startsWith("!")) return;
        String name = message.substring(1);
        if (bites.containsKey(name)) {
            try {
                event.getMessage().delete().block();
            } catch (Exception e) {
                log.error("Error deleting message: {}", e);
            }
            Guild guild = event.getGuild().block();
            if (playingIn.contains(guild)) return;
            VoiceChannel channel = event.getMember().get().getVoiceState().block().getChannel().block();
            playAudio(name, guild, channel);
        }
    }
    
    public void playAudio(String name, Guild guild, VoiceChannel channel) {
        playingIn.add(guild);
        //if (guild.getClient().) channel.leave();
        AudioPlayer player = BenBot.instance.audioPlayerManager.createPlayer();
        AudioSender sender = new AudioSender(player);
        VoiceConnection[] vc = new VoiceConnection[] {null};
        player.addListener(p -> {
            if (p instanceof TrackEndEvent) {
                p.player.destroy();
                sender.done = true;
                vc[0].disconnect();
                playingIn.remove(guild);
            }
        });
        VoiceConnection c = channel.join(spec -> {
            spec.setProvider(sender);
        }).block();
        vc[0] = c;

        //guild.getAudioManager().setAudioProvider(sender);
        bites.put(name, bites.get(name).makeClone());
        player.playTrack(bites.get(name));
    }
    
    public void playAudio(String name) {
        User user = BenBot.instance.client.getSelf().block();
        for (Guild guild : BenBot.instance.client.getGuilds().toIterable()) {
            VoiceState v = guild.getVoiceStates().filter(a -> a.getUser().block().equals(user)).blockFirst();
            if (v != null) {
                playAudio(name, guild, v.getChannel().block());
            }
        }
    }
    
//    public void playAudio(String name) {
//        playAudio(name, BenBot.instance.client.getUserByID(66312966248607744L));
//    }
    
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
    
    class AudioSender extends AudioProvider {
        private AudioPlayer player;
        private MutableAudioFrame frame = new MutableAudioFrame();
        boolean done = false;
        public AudioSender(AudioPlayer player) {
            super(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()));
            this.player = player;
            this.frame.setBuffer(getBuffer());
        }
    
        @Override
        public boolean provide() {
            boolean didProvide = player.provide(frame);
            if (didProvide) getBuffer().flip();
            return didProvide;
        }
    }
    
}
