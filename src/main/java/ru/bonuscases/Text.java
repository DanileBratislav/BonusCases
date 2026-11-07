package ru.bonuscases;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;

/** Мелкие утилиты: цвета через &, числа, склонение. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Строка с &-цветами -> Component без курсива (чтобы названия и lore не были курсивом). */
    public static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    /** 15.0 -> "15", 2.5 -> "2.50" */
    public static String num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return String.format(Locale.US, "%.2f", d);
    }

    /** Процент без лишних нулей: 12.50 -> "12.5", 5.00 -> "5". */
    public static String pct(double p) {
        String s = String.format(Locale.US, "%.2f", p);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /** Русское склонение: 1 нарт, 2 нарта, 5 нартов. */
    public static String plural(long n, String one, String few, String many) {
        long m100 = Math.abs(n) % 100;
        long m10 = m100 % 10;
        if (m100 >= 11 && m100 <= 14) {
            return many;
        }
        if (m10 == 1) {
            return one;
        }
        if (m10 >= 2 && m10 <= 4) {
            return few;
        }
        return many;
    }
}
