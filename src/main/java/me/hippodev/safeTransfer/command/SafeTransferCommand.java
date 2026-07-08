package me.hippodev.safeTransfer.command;

import me.hippodev.safeTransfer.debug.DebugLogger;
import me.hippodev.safeTransfer.message.MessageService;
import me.hippodev.safeTransfer.transfer.TransferService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SafeTransferCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "safetransfer.use";
    private static final int MAX_RECENT_ADDRESSES = 10;

    private final Plugin plugin;
    private final MessageService messages;
    private final DebugLogger debug;
    private final TransferService transferService;

    // Most-recently-used addresses first, so admins can tab through hosts
    // they've already transferred to instead of retyping them.
    private final Set<String> recentAddresses = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_RECENT_ADDRESSES;
        }
    });

    public SafeTransferCommand(Plugin plugin, MessageService messages, DebugLogger debug, TransferService transferService) {
        this.plugin = plugin;
        this.messages = messages;
        this.debug = debug;
        this.transferService = transferService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            messages.send(sender, "usage", Placeholder.unparsed("command", label));
            return true;
        }

        String targetSelector = args[0];
        String address = args[1];
        int delayTicks = plugin.getConfig().getInt("default-delay-ticks", 20);
        if (args.length >= 3) {
            try {
                delayTicks = Math.max(0, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                messages.send(sender, "invalid-delay");
                return true;
            }
        }

        debug.log("Command invoked by " + sender.getName() + ": target=" + targetSelector
                + " address=" + address + " delayTicks=" + delayTicks);

        List<Player> targets = resolveTargets(sender, targetSelector);
        if (targets.isEmpty()) {
            messages.send(sender, "no-players-matched", Placeholder.unparsed("target", targetSelector));
            return true;
        }

        recentAddresses.add(address);
        transferService.transfer(sender, targets, address, delayTicks);
        return true;
    }

    private List<Player> resolveTargets(CommandSender sender, String selector) {
        if (selector.startsWith("@")) {
            try {
                List<Entity> entities = Bukkit.getServer().selectEntities(sender, selector);
                return entities.stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                messages.send(sender, "invalid-selector", Placeholder.unparsed("reason", String.valueOf(e.getMessage())));
                return Collections.emptyList();
            }
        }

        Player player = Bukkit.getPlayerExact(selector);
        if (player == null) {
            return Collections.emptyList();
        }
        List<Player> result = new ArrayList<>();
        result.add(player);
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("@a");
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filterByPrefix(options, args[0]);
        }

        if (args.length == 2) {
            List<String> options = new ArrayList<>(recentAddresses);
            if (!options.contains("localhost")) {
                options.add("localhost");
            }
            return filterByPrefix(options, args[1]);
        }

        if (args.length == 3) {
            List<String> options = new ArrayList<>();
            options.add(String.valueOf(plugin.getConfig().getInt("default-delay-ticks", 20)));
            options.addAll(List.of("0", "20", "40", "60", "100"));
            return filterByPrefix(options.stream().distinct().collect(Collectors.toList()), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterByPrefix(List<String> options, String typed) {
        String prefix = typed.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
    }
}
