package ru.bonuscases;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class CaseListener implements Listener {
    private final BonusCasesPlugin plugin;
    private final Menus menus;

    public CaseListener(BonusCasesPlugin plugin) {
        this.plugin = plugin;
        this.menus = plugin.menus();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        CaseBlock cb = plugin.cases().at(b);
        if (cb == null) {
            return;
        }
        Player p = e.getPlayer();

        boolean building = p.isSneaking() && p.hasPermission("bonuscase.admin")
                && !p.getInventory().getItemInMainHand().getType().isAir();
        if (building) {
            return;
        }

        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);

        if (!p.hasPermission("bonuscase.use")) {
            p.sendMessage(plugin.msg("no-use"));
            return;
        }
        if (plugin.sessions().containsKey(p.getUniqueId())) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            menus.openConfirm(p, cb);
        } else {
            menus.openPreview(p, cb);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.cases().at(e.getBlock()) == null) {
            return;
        }
        e.setCancelled(true);
        if (e.getPlayer().hasPermission("bonuscase.admin")) {
            e.getPlayer().sendMessage(plugin.msg("break-denied"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.cases().at(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.cases().at(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.cases().at(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.cases().at(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof WheelSession) {
            e.setCancelled(true);
            return;
        }
        if (!(holder instanceof CaseMenu menu)) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        switch (menu.type()) {
            case CONFIRM -> {
                e.setCancelled(true);
                int raw = e.getRawSlot();
                if (raw < 0 || raw >= e.getInventory().getSize()) {
                    return;
                }
                if (raw == Menus.CONFIRM_SLOT) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.tryOpen(p, menu.block()));
                } else if (raw == Menus.PREVIEW_SLOT) {
                    Bukkit.getScheduler().runTask(plugin, () -> menus.openPreview(p, menu.block()));
                }
            }
            case PREVIEW -> {
                e.setCancelled(true);
                if (e.getRawSlot() == Menus.BACK_SLOT && menu.block() != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> menus.openConfirm(p, menu.block()));
                }
            }
            case EDITOR -> handleEditorClick(e, p);
        }
    }

    private void handleEditorClick(InventoryClickEvent e, Player p) {
        Inventory top = e.getInventory();
        int raw = e.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();
        boolean inLoot = inTop && raw < Menus.LOOT_SLOTS;
        ClickType click = e.getClick();
        ItemStack cursor = e.getCursor();

        if (raw == Menus.TRASH_SLOT) {
            e.setCancelled(true);
            if (cursor != null && !cursor.getType().isAir()) {
                p.setItemOnCursor(null);
            }
            return;
        }
        if (inTop && !inLoot) {
            e.setCancelled(true);
            return;
        }

        if (inLoot && (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT
                || click == ClickType.DROP || click == ClickType.CONTROL_DROP)) {
            e.setCancelled(true);
            ItemStack it = top.getItem(raw);
            if (it == null || it.getType().isAir()) {
                return;
            }
            int delta = switch (click) {
                case SHIFT_LEFT -> 1;
                case SHIFT_RIGHT -> -1;
                case DROP -> 10;
                default -> -10;
            };
            int w = Math.max(1, Math.min(100000, menus.weightOf(it) + delta));
            top.setItem(raw, menus.withWeight(it, w));
            scheduleRefresh(top);
            return;
        }

        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            e.setCancelled(true);
            return;
        }
        if (menus.isMarked(cursor) && !inLoot) {
            e.setCancelled(true);
            return;
        }
        scheduleRefresh(top);
    }

    private void scheduleRefresh(Inventory top) {
        Bukkit.getScheduler().runTask(plugin, () -> menus.refreshEditor(top));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof WheelSession) {
            e.setCancelled(true);
            return;
        }
        if (!(holder instanceof CaseMenu menu)) {
            return;
        }
        if (menu.type() != CaseMenu.Type.EDITOR) {
            e.setCancelled(true);
            return;
        }
        int size = e.getInventory().getSize();
        boolean marked = menus.isMarked(e.getOldCursor());
        for (int s : e.getRawSlots()) {
            if (s >= Menus.LOOT_SLOTS && (s < size || marked)) {
                e.setCancelled(true);
                return;
            }
        }
        scheduleRefresh(e.getInventory());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof WheelSession ws) {
            ws.finish(false);
            return;
        }
        if (holder instanceof CaseMenu menu && menu.type() == CaseMenu.Type.EDITOR) {
            HumanEntity who = e.getPlayer();
            ItemStack cursor = who.getItemOnCursor();
            if (menus.isMarked(cursor)) {
                who.setItemOnCursor(menus.undecorate(cursor));
            }
            List<LootEntry> entries = menus.collectEditor(e.getInventory());
            plugin.cases().setLoot(menu.number(), entries);
            who.sendMessage(plugin.msg("editor-saved",
                    "number", String.valueOf(menu.number()),
                    "count", String.valueOf(entries.size())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        WheelSession s = plugin.sessions().get(e.getPlayer().getUniqueId());
        if (s != null) {
            s.finish(false);
        }
    }
}
