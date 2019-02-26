import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.gateway.retry.RetryOptions;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class BenBot {
    
    
    static Logger log = LoggerFactory.getLogger(BenBot.class.getName());
    
    public static BenBot instance;
    
    public DiscordClient client;
    public AudioBiteHandler audioBites;
    public AudioPlayerManager audioPlayerManager;
    public List<String> commands;
    public CommandHandler commandHandler;
    
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        new BenBot();
        //YoutubeDownload.trim("OmaeWa.wav", 1, 2);
        /*Future<File> f = YoutubeDownload.download("https://www.youtube.com/watch?v=2P5qbcRAXVk", "test");
        System.out.println("a");
        File fi = f.get();
        System.out.println("b");
        System.out.println(fi);*/
    }
    
    public BenBot() throws IOException {
        instance = this;
        DiscordClientBuilder discordClientBuilder = new DiscordClientBuilder(Files.readAllLines(Paths.get("token.txt")).get(0).trim());
        client = discordClientBuilder.build();
        client.getEventDispatcher().on(ReadyEvent.class).subscribe(ready -> {
            log.info("Logged in as {}", ready.getSelf().getUsername());
        });

        commandHandler = new CommandHandler();
        commandHandler.bind(client);
        commandHandler.registerCommands(Commands.class);

        client.getEventDispatcher().on(MessageCreateEvent.class).subscribe(AudioUploader::onMessageRecievedEvent);

        audioPlayerManager = new DefaultAudioPlayerManager();

        audioBites = new AudioBiteHandler(client);
        client.getEventDispatcher().on(MessageCreateEvent.class).subscribe(audioBites::onMesageReveivedEvent);

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.WARNING);

        logger.setUseParentHandlers(false);

        client.login().block();
    }

}
