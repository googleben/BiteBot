import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.User;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class CommandInfo {
    Command annotation;
    Method method;

    public CommandInfo(Command annotation, Method method) {
        this.annotation = annotation;
        this.method = method;
    }
}

public class CommandHandler {

    private DiscordClient client;
    private Map<String, CommandInfo> commands = new HashMap<>();
    private String prefix = "!";

    public CommandHandler() {}

    public void bind(DiscordClient client) {
        this.client = client;
        client.getEventDispatcher().on(MessageCreateEvent.class).subscribe(this::onMessage);
    }

    public Collection<CommandInfo> getCommands() {
        return commands.values();
    }

    public void registerCommands(Class<?> clazz) {
        for (Method m : clazz.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || Modifier.isPrivate(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())) continue;
            Command a = m.getAnnotation(Command.class);
            if (a==null) continue;
            for (String s : a.aliases()) {
                commands.put(s, new CommandInfo(a, m));
            }
        }
    }

    public void onMessage(MessageCreateEvent e) {
        System.out.println("Message: "+e);
        Optional<String> oContent = e.getMessage().getContent();
        if (!oContent.isPresent()) return;
        User author = e.getMessage().getAuthor().get();
        String content = oContent.get().trim();
        if (!content.startsWith(prefix)) return;
        content = content.substring(prefix.length());
        BenBot.log.info("Received command from {}: \"{}\"", author, content);
        String command = content.split(" ")[0];
        if (!commands.containsKey(command)) return;
        Method m = commands.get(command).method;
        Object ans = null;
        try {
            ans = m.invoke(null, getParameters(m, e));
        } catch (IllegalAccessException | InvocationTargetException e1) {
            BenBot.log.error("Error: {}", e1);
        }
        if (ans instanceof String) {
            e.getMessage().getChannel().block().createMessage((String)ans).block();
        }
    }

    private Object[] getParameters(Method m, MessageCreateEvent e) {
        Object[] ans = new Object[m.getParameterTypes().length];
        String[] split = e.getMessage().getContent().get().split(" ");
        String[] args = new String[split.length-1];
        System.arraycopy(split, 1, args, 0, args.length);
        for (int i = 0; i < ans.length; i++) {
            Class<?> type = m.getParameterTypes()[i];
            if (type==String[].class) {
                ans[i] = args;
            }
            if (type==MessageCreateEvent.class) {
                ans[i] = e;
            }
            if (type==User.class) {
                ans[i] = e.getMessage().getAuthor().get();
            }
            if (type==MessageChannel.class) {
                ans[i] = e.getMessage().getChannel().block();
            }
            if (type==Guild.class) {
                ans[i] = e.getGuild().block();
            }
            if (type==Message.class) {
                ans[i] = e.getMessage();
            }
        }
        return ans;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
