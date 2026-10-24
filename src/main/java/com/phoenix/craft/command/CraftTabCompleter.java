package com.phoenix.craft.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CraftTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "rods", "rod", "baits", "bait", "stats", "achievements",
            "search", "recipes", "sell", "help");

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();

        String prefix = args[0].toLowerCase();
        List<String> out = new ArrayList<>();

        // Always include basic subcommands
        for (String s : SUBCOMMANDS) {
            if (s.startsWith(prefix)) out.add(s);
        }

        // Add reload only for admins (matches CraftCommand permission check)
        if (sender.hasPermission("phoenixcraft.admin") && "reload".startsWith(prefix)) {
            out.add("reload");
        }
        if (sender.hasPermission("phoenixcraft.admin") && "rl".startsWith(prefix)) {
            out.add("rl");
        }
        if (sender.hasPermission("phoenixcraft.admin") && "giverecipe".startsWith(prefix)) {
            out.add("giverecipe");
        }

        return out;
    }
}
