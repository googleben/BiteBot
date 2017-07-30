import de.btobastian.sdcf4j.Command;
import de.btobastian.sdcf4j.CommandExecutor;
import de.btobastian.sdcf4j.CommandHandler;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

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
            if (BenBot.audioBites.bites.containsKey(command)) {
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
    
    @Command(aliases = "list", description = "Lists all sound bites available")
    public String listCommand() {
        List<String> bites = new ArrayList<>(BenBot.audioBites.bites.keySet());
        bites.sort(String::compareTo);
        StringBuilder ans = new StringBuilder();
        ans.append("```\n");
        int longest = bites.stream().map(String::length).max(Integer::compareTo).get()+1;
        for (String s : bites) {
            ans.append(s);
            for (int i = s.length(); i<longest; i++) ans.append(" ");
        }
        ans.append("\n```");
        return ans.toString();
        //return "```"+BenBot.audioBites.bites.keySet().stream().reduce("",(a, b) -> a+" "+b).substring(1)+"```";
    }
    
    @Command(aliases = "reload", description = "Reloads list of sound bites")
    public void reloadCommand(IMessage message) {
        BenBot.audioBites.registerFiles();
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
                String id = BenBot.audioBites.bites.get(name).getIdentifier();
                try {
                    Files.move(Paths.get(id), Paths.get("./bites/oldBites/"+name+id.substring(id.length()-4)));
                    BenBot.audioBites.registerFiles();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        
                return "Vote on \""+name+"\" successful.";
            }
            return "";
        }
        String param = Arrays.stream(args).reduce("", (a, b) -> a+" "+b).trim();
        if (!BenBot.audioBites.bites.containsKey(param)) return "No such bite \""+param+"\"";
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
