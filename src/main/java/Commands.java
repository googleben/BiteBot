import com.vdurmont.emoji.Emoji;
import de.btobastian.sdcf4j.Command;
import de.btobastian.sdcf4j.CommandExecutor;
import de.btobastian.sdcf4j.CommandHandler;
import sx.blah.discord.handle.impl.obj.EmojiImpl;
import sx.blah.discord.handle.impl.obj.Reaction;
import sx.blah.discord.handle.obj.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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
    
    private String currentVote = null;
    private Set<IUser> voters = null;
    private IMessage votesCount = null;
    private IMessage timer = null;
    private static final int VOTES_NEEDED = 2;
    
    @Command(aliases = "voteremove", description = "Starts a vote to remove a sound bite", usage = "voteremove <bite>")
    public String voteRemoveCommand(String[] args, IUser sender, IChannel channel) {
        if (currentVote!=null) {
            if (args.length!=0) return "A vote is already in session!";
            voters.add(sender);
            votesCount.edit(voters.size()+" vote to remove \""+currentVote+"\"");
            if (voters.size()==VOTES_NEEDED) {
                String name = currentVote;
                votesCount = null;
                currentVote = null;
                voters = null;
                String id = BenBot.instance.audioBites.bites.get(name).getIdentifier();
                try {
                    Files.move(Paths.get(id), Paths.get("./bites/oldBites/"+name+id.substring(id.length()-4)));
                    BenBot.instance.audioBites.registerFiles();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        
                return "Vote on \""+name+"\" successful.";
            }
            return "";
        }
        String param = Arrays.stream(args).reduce("", (a, b) -> a+" "+b).trim();
        if (!BenBot.instance.audioBites.bites.containsKey(param)) return "No such bite \""+param+"\"";
        currentVote = param;
        voters = new HashSet<>();
        voters.add(sender);
        
        String timeMessage = "Vote on bite \""+param+"\" started. Type `!voteremove` to vote yes. Vote will end in %d minutes.";
        timer = channel.sendMessage(String.format(timeMessage, 10));
        
        new Thread(() -> {
            try {
                //Thread.sleep(1000*60*10);
                for (int i = 9; i>0; i--) {
                    Thread.sleep(1000*60);
                    timer.edit(String.format(timeMessage, i));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (currentVote==null) return; //if vote was already successful
            currentVote = null;
            voters = null;
            votesCount = null;
            timer.delete();
            timer = null;
            channel.sendMessage("Vote on \""+param+"\" failed.");
        }).start();
        
        votesCount = channel.sendMessage("1 vote to remove \""+param+"\"");
        
        return "";
    }
    
}
