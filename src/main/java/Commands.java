import com.vdurmont.emoji.Emoji;
import de.btobastian.sdcf4j.Command;
import de.btobastian.sdcf4j.CommandExecutor;
import de.btobastian.sdcf4j.CommandHandler;
import sx.blah.discord.handle.impl.obj.EmojiImpl;
import sx.blah.discord.handle.impl.obj.Reaction;
import sx.blah.discord.handle.obj.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor {
    
    private final CommandHandler commandHandler;
    
    public Commands(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }
    
    @Command(aliases = "help", description = "Shows the help page")
    public String helpCommand(IMessage message) {
        String command = message.getContent();
        if (command.trim().equals("!help")) return helpPage();
        command = command.substring(6);
        return helpSingleCommand(command);
    }
    
    private String helpSingleCommand(String command) {
        CommandHandler.SimpleCommand c = commandHandler.getCommands().stream()
                .filter(x -> Arrays.stream(x.getCommandAnnotation().aliases()).filter(s -> s.equals(command)).count()>0)
                .findAny().orElse(null);
        if (c==null) {
            if (BenBot.instance.audioBites.bites.containsKey(command)) {
                return "```!" + command + " | Plays sound bite \"" + command + "\"```";
            }
        } else return "```"+getHelpText(c)+"```";
        return "Command \"" + command + "\" not found.";
    }
    
    private String helpPage() {
        StringBuilder ans = new StringBuilder();
        ans.append("```\n");
        ans.append("!<bite> | Plays a sound bite");
        for (CommandHandler.SimpleCommand command : commandHandler.getCommands()) {
            if (!command.getCommandAnnotation().showInHelpPage()) {
                continue;
            }
            ans.append("\n");
            ans.append(getHelpText(command));
        }
        ans.append("\n```");
        return ans.toString();
    }
    
    private String getHelpText(CommandHandler.SimpleCommand command) {
        String ans = "";
        if (!command.getCommandAnnotation().requiresMention()) {
            ans+=commandHandler.getDefaultPrefix();
        }
        String usage = command.getCommandAnnotation().usage();
        if (usage.isEmpty()) {
            usage = command.getCommandAnnotation().aliases()[0];
        }
        ans+=usage;
        String description = command.getCommandAnnotation().description();
        if (!description.equals("none")) {
            ans+=" | "+description;
        }
        return ans;
    }
    
    @Command(aliases = "ping", description="Pong")
    public String pingCommand() {
        return "Pong!";
    }
    
    @Command(aliases = "link", description = "Sends the link to add this bot to a server")
    public String linkCommand() {
        return "https://discordapp.com/oauth2/authorize?client_id=168940312499060736&scope=bot&permissions=2146958591";
    }
    
    final int PER_PAGE = 50;
    
    @Command(aliases = {"list", "List"}, description = "Lists all sound bites available")
    public String listCommand(IChannel channel, IUser user) {
        new Thread(() -> {
            List<String> bites = new ArrayList<>(BenBot.instance.audioBites.bites.keySet());
            List<List<String>> pages = new ArrayList<>();
            bites.sort(String::compareTo);
            for (int i = 0; i<bites.size(); i+=PER_PAGE) {
                pages.add(bites.stream().skip(i).limit(PER_PAGE).collect(Collectors.toList()));
            }
            int page = 0;
            int pagen = pages.size();
            IMessage message = channel.sendMessage(buildList(pages.get(0), 0, pagen));
    
            message.addReaction(":arrow_left:");
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            message.addReaction(":arrow_right:");
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            message.addReaction(":wastebasket:");
    
            int time = 0;
            int interval = 500;
            while (time<1000*60) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    message.delete();
                    return;
                }
                for (IReaction r : message.getReactions()) {
                    if (r.getUnicodeEmoji().getDescription().equals("wastebasket") && r.getUserReacted(user)) {
                        message.delete();
                        return;
                    }
                    if (r.getUnicodeEmoji().getDescription().equals("leftwards black arrow") && r.getUserReacted(user)) {
                        message.removeReaction(user, r);
                        if (page==0) continue;
                        page--;
                        message.edit(buildList(pages.get(page), page, pagen));
                    }
                    if (r.getUnicodeEmoji().getDescription().equals("black rightwards arrow") && r.getUserReacted(user)) {
                        message.removeReaction(user, r);
                        if (page==pagen-1) continue;
                        page++;
                        message.edit(buildList(pages.get(page), page, pagen));
                    }
                }
                time+=interval;
            }
            message.delete();
        }).start();
        return "";
        //return "```"+BenBot.audioBites.bites.keySet().stream().reduce("",(a, b) -> a+" "+b).substring(1)+"```";
    }
    
    private String buildList(List<String> names, int page, int pages) {
        StringBuilder ans = new StringBuilder();
        ans.append(String.format("**Page %d of %d**\n", page+1, pages));
        ans.append("```\n");
        int longest = names.stream().map(String::length).max(Integer::compareTo).orElse(0)+1;
        for (String s : names) {
            ans.append(s);
            for (int i = s.length(); i<longest; i++) ans.append(" ");
        }
        ans.append("\n```");
        return ans.toString();
    }
    
    @Command(aliases = "reload", description = "Reloads list of sound bites")
    public void reloadCommand(IMessage message) {
        BenBot.instance.audioBites.registerFiles();
        message.delete();
    }
    
    @Command(aliases = "rem", showInHelpPage = false)
    public String rem() {
        return "!voteremove";
    }
    
    static final int VOTES_NEEDED = 1;
    
    @Command(aliases = "voteremove", description = "Starts a vote to remove a sound bite", usage = "voteremove <bite>")
    public String voteRemoveCommand(String[] args, IUser sender, IChannel channel) {
        String param = Arrays.stream(args).reduce("", (a, b) -> a+" "+b).trim();
        if (!BenBot.instance.audioBites.bites.containsKey(param)) return "No such bite \""+param+"\"";
        
        String messageS = "Vote on bite `"+param+"` started. React with :arrow_up: to vote.\n"+
                VOTES_NEEDED+" total votes required.\nVote will end in %d minutes.";
        final IMessage message = channel.sendMessage(String.format(messageS, 10));
        message.addReaction(":arrow_up:");
        
        new Thread(() -> {
            int votes = 0;
            int time = 0;
            int interval = 500;
            while (time<10*60*1000) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                time+=interval;
                if (time%(1000*60)==0) message.edit(String.format(messageS, 10-time/(1000*60)));
                votes = message.getReactions().get(0).getCount()-1;
                if (votes==VOTES_NEEDED) {
                    message.delete();
                    String id = BenBot.instance.audioBites.bites.get(param).getIdentifier();
                    try {
                        Files.move(Paths.get(id), Paths.get("./bites/oldBites/"+param+id.substring(id.length()-4)));
                        BenBot.instance.audioBites.registerFiles();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    channel.sendMessage("Bite `"+param+"` successfully voted off the island.");
                }
            }
            message.delete();
            channel.sendMessage("Vote on `"+param+"` failed.");
        }).start();
        
        
        
        return "";
    }
    
    @Command(aliases = "dl", description = "Downloads a portion of a YouTube video as a clip", usage = "dl <link> <name> <startTime> <endTime>")
    public String dlCommand(String c, String url, String name, String startS, String endS) {
        if (BenBot.instance.audioBites.bites.containsKey(name)) return "There's already a bite with name `"+name+"`!";
        int start = parseTime(startS);
        int end = parseTime(endS);
        if (start==-1 || end==-1) return "Format times as `s.ms`, `m:s.ms`, or `h:m:s.ms` please.";
        if (start>=end) return "End must be later than start.";
        if (end-start>30*1000) return "No clips longer than 30 seconds please!";
        File f;
        try {
            f = YoutubeDownload.download(url, name, start, end).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return "Error while downloading.";
        }
        f.renameTo(new File("bites/"+name+".wav"));
        BenBot.instance.audioBites.registerFiles();
        return "Done.";
    }
    
    private int parseTime(String time) {
        int ans = 0;
        int mul = 1000;
        String[] times = time.split(":");
        if (times[times.length-1].matches(".+\\..+")) {
            String[] sMs = times[times.length-1].split("\\.");
            times[times.length-1] = sMs[0];
            if (sMs[1].length()>1) sMs[1] = sMs[1].substring(0, 1);
            try {
                ans+=Integer.parseInt(sMs[1])*500;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        for (int i = times.length-1; i>=0; i--) {
            try {
                ans+=Integer.parseInt(times[i])*mul;
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return -1;
            }
        }
        return ans;
    }
    
}
