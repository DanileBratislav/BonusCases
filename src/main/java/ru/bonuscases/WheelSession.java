package ru.bonuscases;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Колесо призов. Награды лежат по кругу (рамка инвентаря на 45 слотов, 24 ячейки),
 * подсветка бежит по часовой стрелке, замедляется и останавливается на выигрышной ячейке.
 * Приз выбирается заранее, до старта анимации, и оплата уже списана - поэтому при любом
 * досрочном закрытии (закрыл окно, вышел, рестарт) приз выдаётся сразу.
 */
public final class WheelSession implements InventoryHolder {

    /** Слоты рамки по часовой стрелке, начиная с левого верхнего угла. */
    public static final int[] RING = new int[24];
    public static final int CENTER = 22;

    static {
        int i = 0;
        for (int c = 0; c <= 8; c++) {
            RING[i++] = c;
        }
        for (int r = 1; r <= 3; r++) {
            RING[i++] = r * 9 + 8;
        }
        for (int c = 8; c >= 0; c--) {
            RING[i++] = 4 * 9 + c;
        }
        for (int r = 3; r >= 1; r--) {
            RING[i++] = r * 9;
        }
    }

    private final BonusCasesPlugin plugin;
    private final Menus menus;
    private final Player player;
    private final CaseBlock caseBlock;
    private final LootEntry prize;
    private final List<LootEntry> pool;
    private final Inventory inv;
    private final LootEntry[] ringEntries = new LootEntry[RING.length];
    private final int winnerPos;
    private final int totalSteps;

    private BukkitTask task;
    private int step = 0;
    private int wait = 1;
    private int lit = -1;
    private boolean landed = false;
    private boolean finished = false;

    public WheelSession(BonusCasesPlugin plugin, Player player, CaseBlock caseBlock, LootEntry prize,
                        List<LootEntry> pool) {
        this.plugin = plugin;
        this.menus = plugin.menus();
        this.player = player;
        this.caseBlock = caseBlock;
        this.prize = prize;
        this.pool = pool;

        List<LootEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        for (int i = 0; i < RING.length; i++) {
            ringEntries[i] = shuffled.get(i % shuffled.size());
        }
        this.winnerPos = ThreadLocalRandom.current().nextInt(RING.length);
        ringEntries[winnerPos] = prize;
        this.totalSteps = RING.length * 2 + winnerPos;

        this.inv = Bukkit.createInventory(this, 45,
                Text.c("&8Колесо: " + plugin.cases().caseName(caseBlock.number())));
        ItemStack pane = menus.named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, pane);
        }
        for (int i = 0; i < RING.length; i++) {
            inv.setItem(RING[i], shown(i, false));
        }
        inv.setItem(CENTER, menus.named(Material.NETHER_STAR, "&e&lКрутим колесо...", "&7Удачи!"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    /** Открывает колесо (на следующем тике) и запускает прокрутку. */
    public void start() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (finished) {
                return;
            }
            if (!player.isOnline()) {
                finish(false);
                return;
            }
            player.openInventory(inv);
            light(0);
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    tick();
                }
            }.runTaskTimer(plugin, 10L, 1L);
        });
    }

    private void tick() {
        if (finished) {
            return;
        }
        if (--wait > 0) {
            return;
        }
        if (landed) {
            finish(true);
            return;
        }
        step++;
        light(step % RING.length);
        double progress = (double) step / totalSteps;
        sound("minecraft:ui.button.click", 0.7f, (float) (0.8 + 1.0 * progress));
        if (step >= totalSteps) {
            landed = true;
            wait = 35;
            ItemStack win = menus.display(prize, chance(prize), true, "&a&lВАШ ВЫИГРЫШ!");
            inv.setItem(CENTER, win);
            sound("minecraft:entity.player.levelup", 1f, 1f);
            return;
        }
        wait = 1 + (int) (progress * progress * progress * 8);
    }

    private void light(int pos) {
        if (lit >= 0) {
            inv.setItem(RING[lit], shown(lit, false));
        }
        lit = pos;
        inv.setItem(RING[pos], shown(pos, true));
        inv.setItem(CENTER, shown(pos, true));
    }

    private ItemStack shown(int pos, boolean glint) {
        LootEntry e = ringEntries[pos];
        return menus.display(e, chance(e), glint, null);
    }

    private double chance(LootEntry e) {
        return CaseManager.chance(e, pool);
    }

    private void sound(String key, float volume, float pitch) {
        if (player.isOnline()) {
            player.playSound(player.getLocation(), key, volume, pitch);
        }
    }

    /** Завершает сессию и выдаёт приз. Безопасно вызывать несколько раз. */
    public void finish(boolean closeInventory) {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
        }
        plugin.sessions().remove(player.getUniqueId());
        award();
        if (closeInventory && player.isOnline()) {
            player.closeInventory();
        }
    }

    private void award() {
        ItemStack give = prize.item().clone();
        Map<Integer, ItemStack> left = player.getInventory().addItem(give.clone());
        for (ItemStack rest : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }

        Component name = give.displayName();
        String amount = give.getAmount() > 1 ? " &7x" + give.getAmount() : "";
        if (player.isOnline()) {
            player.sendMessage(plugin.msg("won").append(name).append(Text.c(amount)));
        }

        double threshold = plugin.getConfig().getDouble("broadcast-below-percent", 5.0);
        if (threshold > 0 && chance(prize) < threshold) {
            Component broadcast = plugin.msg("broadcast",
                    "player", player.getName(),
                    "case", plugin.cases().caseName(caseBlock.number()))
                    .append(name).append(Text.c(amount));
            Bukkit.broadcast(broadcast);
        }
    }
}
