import org.apache.commons.io.IOUtils;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;

import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;

public class AudioUploader {
    
    @EventSubscriber
    public void onMessageRecievedEvent(MessageReceivedEvent event) {
        if (!event.getChannel().isPrivate()) return;
        BenBot.log.debug("event");
        IMessage.Attachment a = event.getMessage().getAttachments().get(0);
        String name = a.getFilename().substring(0, a.getFilename().length()-4);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (a.getFilename().endsWith(".wav")) {
            if (BenBot.instance.commands.contains(name) || BenBot.instance.audioBites.bites.containsKey(name)) {
                event.getChannel().sendMessage("Sorry, the name \""+name+"\" is the name of an existing bite or command. Please rename your bite.");
                return;
            }
            if (downloadFile(a.getUrl(), a.getFilename())) {
                BenBot.instance.audioBites.userMap.put(name, event.getAuthor().getLongID());
                BenBot.instance.audioBites.registerFiles();
            }
        } else {
            event.getChannel().sendMessage("Sorry, bites must be in .wav format.");
        }
        
    }
    
    public static boolean downloadFile(String urlString, String filename) {
        try {
            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.3; rv:36.0) Gecko/20100101 Firefox/36.0");
            conn.connect();
            //HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
            //ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            FileOutputStream fos = new FileOutputStream("bites/"+filename);
            //fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            IOUtils.copy(conn.getInputStream(), fos);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }
    
}
