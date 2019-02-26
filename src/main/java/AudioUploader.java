import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Message;
import org.apache.commons.io.IOUtils;

import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;

public class AudioUploader {

    public static void onMessageRecievedEvent(MessageCreateEvent event) {
        //if (!event.getMessage().getChannel.getValue().isPrivate()) return;
        BenBot.log.debug("event");
        Attachment a = event.getMessage().getAttachments().toArray(new Attachment[0])[0];
        String name = a.getFilename().substring(0, a.getFilename().length()-4);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (a.getFilename().endsWith(".wav")) {
            if (BenBot.instance.commands.contains(name) || BenBot.instance.audioBites.bites.containsKey(name)) {
                event.getMessage().getChannel().block().createMessage("Sorry, the name \""+name+"\" is the name of an existing bite or command. Please rename your bite.").block();
                return;
            }
            if (downloadFile(a.getUrl(), a.getFilename())) {
                BenBot.instance.audioBites.userMap.put(name, event.getMessage().getAuthor().get().getId().asLong());
                BenBot.instance.audioBites.registerFiles();
            }
        } else {
            event.getMessage().getChannel().block().createMessage("Sorry, bites must be in .wav format.").block();
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
