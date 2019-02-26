import discord4j.core.DiscordClient;
import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.reaction.Reaction;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.object.util.Snowflake;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class Commands {
    
    @Command(aliases = "help", description = "Shows the help page")
    public static String helpCommand(Message message) {
        String command = message.getContent().get();
        if (command.trim().equals("!help")) return helpPage();
        command = command.substring(6);
        return helpSingleCommand(command);
    }
    
    private static String helpSingleCommand(String command) {
        CommandInfo c = BenBot.instance.commandHandler.getCommands().stream()
                .filter(x -> Arrays.asList(x.annotation.aliases()).contains(command))
                .findAny().orElse(null);
        if (c==null) {
            if (BenBot.instance.audioBites.bites.containsKey(command)) {
                return "```!" + command + " | Plays sound bite \"" + command + "\"```";
            }
        } else return "```"+getHelpText(c)+"```";
        return "Command \"" + command + "\" not found.";
    }
    
    private static String helpPage() {
        StringBuilder ans = new StringBuilder();
        ans.append("```\n");
        ans.append("!<bite> | Plays a sound bite");
        for (CommandInfo command : BenBot.instance.commandHandler.getCommands()) {
            if (!command.annotation.showInHelpPage()) {
                continue;
            }
            ans.append("\n");
            ans.append(getHelpText(command));
        }
        ans.append("\n```");
        return ans.toString();
    }
    
    private static String getHelpText(CommandInfo command) {
        String ans = "";
        CommandHandler commandHandler = BenBot.instance.commandHandler;
        ans+=commandHandler.getPrefix();
        String usage = command.annotation.usage();
        if (usage.isEmpty()) {
            usage = command.annotation.aliases()[0];
        }
        ans+=usage;
        String description = command.annotation.description();
        if (!description.equals("none")) {
            ans+=" | "+description;
        }
        return ans;
    }
    
    @Command(aliases = "ping", description="Pong")
    public static String pingCommand() {
        return "Pong!";
    }
    
    @Command(aliases = "link", description = "Sends the link to add this bot to a server")
    public static String linkCommand() {
        return "https://discordapp.com/oauth2/authorize?client_id=168940312499060736&scope=bot&permissions=2146958591";
    }
    
    private final static int PER_PAGE = 50;
    
    @Command(aliases = {"list", "List"}, description = "Lists all sound bites available")
    public static String listCommand(MessageChannel channel, User user) {
        new Thread(() -> {
            List<String> bites = new ArrayList<>(BenBot.instance.audioBites.bites.keySet());
            List<List<String>> pages = new ArrayList<>();
            bites.sort(String::compareTo);
            for (int i = 0; i<bites.size(); i+=PER_PAGE) {
                pages.add(bites.stream().skip(i).limit(PER_PAGE).collect(Collectors.toList()));
            }
            int page = 0;
            int pagen = pages.size();
            Message message = channel.createMessage(buildList(pages.get(0), 0, pagen)).block();
    
            message.addReaction(ReactionEmoji.unicode("◀")).block();
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            message.addReaction(ReactionEmoji.unicode("▶")).block();
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            message.addReaction(ReactionEmoji.unicode("\uD83D\uDDD1")).block();
    
            int time = 0;
            int interval = 500;
            Snowflake channelId = message.getChannelId();
            Snowflake messageId = message.getId();
            while (time<1000*60) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    message.delete().block();
                    return;
                }
                message = BenBot.instance.client.getMessageById(channelId, messageId).block();
                for (Reaction r : message.getReactions()) {
                    if (r.getCount() < 2) continue;
                    try {
                        if (!r.getEmoji().asUnicodeEmoji().isPresent()) continue;
                        r.getEmoji().asUnicodeEmoji().get();
                    } catch (Exception e) {
                        continue;
                    }
                    if (r.getEmoji().asUnicodeEmoji().get().getRaw().equals("\uD83D\uDDD1")) {
                        message.delete().block();
                        return;
                    }
                    if (r.getEmoji().asUnicodeEmoji().get().getRaw().equals("◀")) {
                        message.removeReaction(r.getEmoji(), user.getId()).block();
                        if (page==0) continue;
                        page--;
                        int finalPage = page;
                        message.edit(spec -> spec.setContent(buildList(pages.get(finalPage), finalPage, pagen))).block();
                    }
                    if (r.getEmoji().asUnicodeEmoji().get().getRaw().equals("▶")) {
                        message.removeReaction(r.getEmoji(), user.getId()).block();
                        if (page==pagen-1) continue;
                        page++;
                        int finalPage = page;
                        message.edit(spec -> spec.setContent(buildList(pages.get(finalPage), finalPage, pagen))).block();
                    }
                }
                time+=interval;
            }
            message.delete().block();
        }).start();
        return "";
        //return "```"+BenBot.audioBites.bites.keySet().stream().reduce("",(a, b) -> a+" "+b).substring(1)+"```";
    }
    
    private static String buildList(List<String> names, int page, int pages) {
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
    public static void reloadCommand(Message message) {
        BenBot.instance.audioBites.registerFiles();
        message.delete().block();
    }
    
    @Command(aliases = "rem", showInHelpPage = false)
    public static String rem() {
        return "!voteremove";
    }
    
    static final int VOTES_NEEDED = 1;
    
    @Command(aliases = "voteremove", description = "Starts a vote to remove a sound bite", usage = "voteremove <bite>")
    public static String voteRemoveCommand(String[] args, User sender, MessageChannel channel) {
        String param = Arrays.stream(args).reduce("", (a, b) -> a+" "+b).trim();
        if (!BenBot.instance.audioBites.bites.containsKey(param)) return "No such bite \""+param+"\"";
        
        String messageS = "Vote on bite `"+param+"` started. React with :arrow_up: to vote.\n"+
                VOTES_NEEDED+" total votes required.\nVote will end in %d minutes.";
        final Message message = channel.createMessage(String.format(messageS, 10)).block();
        message.addReaction(ReactionEmoji.unicode("⬆")).block();
        
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
                if (time%(1000*60)==0) {
                    int finalTime = time;
                    message.edit(spec -> spec.setContent(String.format(messageS, 10- finalTime /(1000*60)))).block();
                }
                votes = message.getReactions().toArray(new Reaction[0])[0].getCount()-1;
                if (votes==VOTES_NEEDED) {
                    message.delete();
                    String id = BenBot.instance.audioBites.bites.get(param).getIdentifier();
                    try {
                        Files.move(Paths.get(id), Paths.get("./bites/oldBites/"+param+id.substring(id.length()-4)));
                        BenBot.instance.audioBites.registerFiles();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    channel.createMessage("Bite `"+param+"` successfully voted off the island.").block();
                }
            }
            message.delete().block();
            channel.createMessage("Vote on `"+param+"` failed.").block();
        }).start();
        
        
        
        return "";
    }
    
    @Command(aliases = "dl", description = "Downloads a portion of a YouTube video as a clip", usage = "dl <link> <name> <startTime> <endTime>")
    public static String dlCommand(String c, String url, String name, String startS, String endS) {
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
    
    private static int parseTime(String time) {
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
