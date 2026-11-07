package ru.bonuscases;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Владелец наших меню - по нему слушатель понимает, что инвентарь принадлежит плагину. */
public final class CaseMenu implements InventoryHolder {

    public enum Type {
        /** Меню "Открыть / Содержимое" */
        CONFIRM,
        /** Просмотр наград с шансами */
        PREVIEW,
        /** Админский редактор наград */
        EDITOR
    }

    private final Type type;
    private final CaseBlock block;
    private final int number;
    private Inventory inventory;

    public CaseMenu(Type type, CaseBlock block, int number) {
        this.type = type;
        this.block = block;
        this.number = number;
    }

    public Type type() {
        return type;
    }

    /** Блок-кейс, из которого открыто меню (в редакторе может быть null). */
    public CaseBlock block() {
        return block;
    }

    /** Номер кейса (набора наград). */
    public int number() {
        return number;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
