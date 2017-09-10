import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import de.btobastian.sdcf4j.CommandHandler;
import de.btobastian.sdcf4j.handler.Discord4JHandler;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;

import org.slf4j.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class BenBot {
    
    
    static Logger log = LoggerFactory.getLogger(BenBot.class.getName());
    
    public static BenBot instance;
    
    public IDiscordClient client;
    public CommandHandler commandHandler;
    public AudioBiteHandler audioBites;
    public AudioPlayerManager audioPlayerManager;
    public List<String> commands;
    
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        GlobalScreen.addNativeKeyListener(new KeyListener());
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
        ClientBuilder cb = new ClientBuilder().setMaxReconnectAttempts(-1).withToken(Files.readAllLines(Paths.get("token.txt")).get(0).trim());
        client = cb.login();
        commandHandler = new Discord4JHandler(client);
        commandHandler.setDefaultPrefix("!");
        commandHandler.registerCommand(new Commands(commandHandler));
        client.getDispatcher().registerListener(new AudioUploader());
    
        commands = commandHandler.getCommands().stream().map(c -> c.getCommandAnnotation().aliases()).flatMap(Arrays::stream).collect(Collectors.toList());
    
        audioPlayerManager = new DefaultAudioPlayerManager();
    
        audioBites = new AudioBiteHandler();
        client.getDispatcher().registerListener(audioBites);
    
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.WARNING);
    
        logger.setUseParentHandlers(false);
    
        try {
            GlobalScreen.registerNativeHook();
        }
        catch (NativeHookException ex) {
            System.err.println("There was a problem registering the native hook.");
            System.err.println(ex.getMessage());
        
            System.exit(1);
        }
    }

}
