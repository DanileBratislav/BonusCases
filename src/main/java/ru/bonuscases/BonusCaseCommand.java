package ru.bonuscases;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public final class BonusCaseCommand implements TabExecutor {
    private final BonusCasesPlugin plugin;

    public BonusCaseCommand(BonusCasesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bonuscase.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "remove", "delete", "del" -> remove(sender);
            case "list" -> list(sender);
            case "edit" -> edit(sender, args);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            case "help" -> help(sender);
            default -> create(sender, args);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Text.c("&6&lBonusCases &7- команды:"));
        s.sendMessage(Text.c("&e/bonuscase <цена> <номер> &7- сделать кейсом блок, на который смотрите"));
        s.sendMessage(Text.c("&7  например: &f/bonuscase 15 1&7, &f/bonuscase 30 2&7, &f/bonuscase 50 3"));
        s.sendMessage(Text.c("&e/bonuscase edit <номер> &7- редактор наград кейса"));
        s.sendMessage(Text.c("&e/bonuscase remove &7- убрать кейс с блока, на который смотрите"));
        s.sendMessage(Text.c("&e/bonuscase list &7- все кейсы"));
        s.sendMessage(Text.c("&e/bonuscase reload &7- перезагрузить конфиг"));
    }

    private void create(CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only"));
            return;
        }
        if (a.length < 2) {
            p.sendMessage(plugin.msg("usage-create"));
            return;
        }
        double price;
        int number;
        try {
            price = Double.parseDouble(a[0].replace(',', '.'));
            number = Integer.parseInt(a[1]);
        } catch (NumberFormatException ex) {
            p.sendMessage(plugin.msg("usage-create"));
            return;
        }
        if (!(price > 0) || Double.isInfinite(price) || number < 1 || number > 99) {
            p.sendMessage(plugin.msg("bad-values"));
            return;
        }
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType().isAir()) {
            p.sendMessage(plugin.msg("look-at-block"));
            return;
        }

        boolean existed = plugin.cases().at(b) != null;
        plugin.cases().add(new CaseBlock(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), price, number));
        p.sendMessage(plugin.msg(existed ? "case-updated" : "case-created",
                "name", plugin.cases().caseName(number),
                "number", String.valueOf(number),
                "price", plugin.economy().format(price)));
        if (plugin.cases().loot(number).isEmpty()) {
            p.sendMessage(plugin.msg("no-loot-warning", "number", String.valueOf(number)));
        }
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only"));
            return;
        }
        Block b = p.getTargetBlockExact(6);
        if (b == null) {
            p.sendMessage(plugin.msg("look-at-block"));
            return;
        }
        p.sendMessage(plugin.msg(plugin.cases().remove(b) ? "case-removed" : "not-a-case"));
    }

    private void edit(CommandSender sender, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only"));
            return;
        }
        int number;
        try {
            number = Integer.parseInt(a.length > 1 ? a[1] : "");
        } catch (NumberFormatException ex) {
            p.sendMessage(plugin.msg("edit-usage"));
            return;
        }
        if (number < 1 || number > 99) {
            p.sendMessage(plugin.msg("bad-values"));
            return;
        }
        plugin.menus().openEditor(p, number);
    }

    private void list(CommandSender sender) {
        Collection<CaseBlock> all = plugin.cases().all();
        if (all.isEmpty()) {
            sender.sendMessage(plugin.msg("list-empty"));
            return;
        }
        sender.sendMessage(plugin.msg("list-header", "count", String.valueOf(all.size())));
        int i = 1;
        for (CaseBlock cb : all) {
            Block b = cb.block();
            String missing = (b == null || b.getType().isAir()) ? " &c[блока нет]" : "";
            sender.sendMessage(plugin.msg("list-line",
                    "i", String.valueOf(i++),
                    "world", cb.world(),
                    "x", String.valueOf(cb.x()),
                    "y", String.valueOf(cb.y()),
                    "z", String.valueOf(cb.z()),
                    "number", String.valueOf(cb.number()),
                    "price", plugin.economy().format(cb.price()),
                    "loot", String.valueOf(plugin.cases().loot(cb.number()).size()),
                    "missing", missing));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bonuscase.admin")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("15", "30", "50", "edit", "remove", "list", "reload", "help"));
        } else if (args.length == 2 && (args[0].matches("[0-9.,]+") || args[0].equalsIgnoreCase("edit"))) {
            TreeSet<Integer> nums = new TreeSet<>(List.of(1, 2, 3));
            nums.addAll(plugin.cases().lootNumbers());
            for (CaseBlock cb : plugin.cases().all()) {
                nums.add(cb.number());
            }
            for (int n : nums) {
                out.add(String.valueOf(n));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
