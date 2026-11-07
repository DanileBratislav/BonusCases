package ru.bonuscases;

import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BonusCasesPlugin extends JavaPlugin {
    private CaseManager cases;
    private Menus menus;
    private EconomyBridge economy;
    private final Map<UUID, WheelSession> sessions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cases = new CaseManager(this);
        cases.load();
        menus = new Menus(this);
        economy = new EconomyBridge(this);

        getServer().getPluginManager().registerEvents(new CaseListener(this), this);

        PluginCommand cmd = getCommand("bonuscase");
        if (cmd != null) {
            BonusCaseCommand handler = new BonusCaseCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("Загружено кейсов-блоков: " + cases.all().size());
    }

    @Override
    public void onDisable() {
        for (WheelSession s : new ArrayList<>(sessions.values())) {
            s.finish(true);
        }
        sessions.clear();
    }

    public void reload() {
        reloadConfig();
        cases.load();
    }

    public CaseManager cases() {
        return cases;
    }

    public Menus menus() {
        return menus;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public Map<UUID, WheelSession> sessions() {
        return sessions;
    }

    public Component msg(String key, String... kv) {
        String prefix = getConfig().getString("messages.prefix", "");
        String s = getConfig().getString("messages." + key, "&cНет сообщения: " + key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return Text.c(prefix + s);
    }

    public void tryOpen(Player p, CaseBlock cb) {
        if (sessions.containsKey(p.getUniqueId())) {
            return;
        }
        List<LootEntry> loot = cases.loot(cb.number());
        if (loot.isEmpty()) {
            p.sendMessage(msg("empty"));
            return;
        }
        if (!economy.available()) {
            p.sendMessage(msg("no-economy"));
            return;
        }
        if (!economy.has(p, cb.price())) {
            p.sendMessage(msg("not-enough", "price", economy.format(cb.price())));
            return;
        }
        if (!economy.withdraw(p, cb.price())) {
            p.sendMessage(msg("pay-failed"));
            return;
        }

        try {
            LootEntry prize = cases.pick(cb.number());
            WheelSession session = new WheelSession(this, p, cb, prize, loot);
            sessions.put(p.getUniqueId(), session);
            p.sendMessage(msg("paid", "price", economy.format(cb.price())));
            session.start();
        } catch (RuntimeException ex) {
            sessions.remove(p.getUniqueId());
            economy.deposit(p, cb.price());
            p.sendMessage(msg("pay-failed"));
            getLogger().severe("Ошибка запуска колеса, деньги возвращены: " + ex);
        }
    }
}
