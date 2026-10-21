package ru.adblock.lsposed;

import java.util.Locale;

/** Распознавание коротких текстовых меток рекламы для UI-слоя. */
final class AdText {
    private static final int MAX_LEN = 30;

    private AdText() {
    }

    static boolean matches(CharSequence cs) {
        if (cs == null) return false;
        int n = cs.length();
        if (n < 4 || n > MAX_LEN) return false;
        // Быстрый отсев по первому непробельному символу.
        int i = 0;
        while (i < n && Character.isWhitespace(cs.charAt(i))) i++;
        if (i >= n) return false;
        char c = cs.charAt(i);
        if ("РрEeСсПпSs".indexOf(c) < 0) return false;

        String s = cs.toString().replace(' ', ' ').trim().toLowerCase(Locale.ROOT);
        return word(s, "реклама")
                || word(s, "спонсорский")
                || word(s, "спонсорская")
                || word(s, "спонсорское")
                || word(s, "промо")
                || word(s, "sponsored")
                || s.equals("erid")
                || s.startsWith("erid:")
                || s.startsWith("erid ");
    }

    /** s начинается со слова w, за которым конец строки или не-буква («Реклама ·», «Промо»). */
    private static boolean word(String s, String w) {
        return s.startsWith(w) && (s.length() == w.length() || !Character.isLetter(s.charAt(w.length())));
    }
}
