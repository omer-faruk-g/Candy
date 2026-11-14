package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Yalnız ototest: "#bekle id maxTick regex" adımının beklentilerini tutar.
 * Kök log4j logger'ına bir appender takar; beklenen satır geldiği anı (nanoTime) yakalar.
 * Sonuç istemci tick'inde yazılır (appender içinden log yazmak özyineleme yapar).
 */
final class Senaryo {

    private Senaryo() {}

    private static final class Beklenti {
        final String id; final Pattern desen; final int basTick; final int sonTick;
        final long basNs = System.nanoTime();
        volatile long bulNs = -1; volatile String satir;
        Beklenti(String id, Pattern desen, int basTick, int sonTick) {
            this.id = id; this.desen = desen; this.basTick = basTick; this.sonTick = sonTick;
        }
    }

    private static final List<Beklenti> ACIK = new CopyOnWriteArrayList<>();
    private static int gecti = 0, kaldi = 0;
    private static long topMs = 0, maxMs = 0;
    private static final StringBuilder KALANLAR = new StringBuilder();
    private static boolean takildi = false;

    static void bekle(String id, int maxTick, String regex, int simdi) {
        tak();
        ACIK.add(new Beklenti(id, Pattern.compile(regex), simdi, simdi + maxTick));
    }

    /** Her istemci tick'inde: bulunanları GEÇTİ, süresi dolanları KALDI yaz. */
    static void sur(int tick) {
        if (ACIK.isEmpty()) return;
        Iterator<Beklenti> it = ACIK.iterator();
        while (it.hasNext()) {
            Beklenti b = it.next();
            if (b.bulNs >= 0) {
                long us = (b.bulNs - b.basNs) / 1000L;
                long ms = us / 1000L;
                gecti++; topMs += ms; maxMs = Math.max(maxMs, ms);
                Candy.LOG.warn("[Candy][ototest] SENARYO {} GECTI {}.{} ms (istemci tick {}) :: {}",
                    b.id, ms, String.format("%03d", us % 1000L), tick - b.basTick, kisalt(b.satir));
                ACIK.remove(b);
            } else if (tick >= b.sonTick) {
                kaldi++;
                KALANLAR.append(b.id).append(' ');
                Candy.LOG.warn("[Candy][ototest] SENARYO {} KALDI ({} tick icinde /{}/ gelmedi)",
                    b.id, b.sonTick - b.basTick, b.desen.pattern());
                ACIK.remove(b);
            }
        }
    }

    static void ozet() {
        Candy.LOG.warn("[Candy][ototest] SENARYO OZET gecti={} kaldi={} acik={} ortMs={} maxMs={} kalanlar=[{}]",
            gecti, kaldi, ACIK.size(), gecti == 0 ? 0 : topMs / gecti, maxMs, KALANLAR.toString().strip());
    }

    private static String kisalt(String s) {
        return s == null ? "" : (s.length() > 140 ? s.substring(0, 140) + "…" : s);
    }

    private static synchronized void tak() {
        if (takildi) return;
        takildi = true;
        try {
            org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
            org.apache.logging.log4j.core.appender.AbstractAppender app =
                new org.apache.logging.log4j.core.appender.AbstractAppender("candy-senaryo", null, null, true,
                        org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                    @Override public void append(org.apache.logging.log4j.core.LogEvent e) {
                        if (ACIK.isEmpty()) return;
                        long simdi = System.nanoTime();
                        String m = e.getMessage() == null ? "" : e.getMessage().getFormattedMessage();
                        if (m.contains("[ototest]")) return;
                        for (Beklenti b : ACIK) {
                            if (b.bulNs < 0 && b.desen.matcher(m).find()) { b.satir = m; b.bulNs = simdi; }
                        }
                    }
                };
            app.start();
            ctx.getConfiguration().addAppender(app);
            ctx.getRootLogger().addAppender(app);
            Candy.LOG.warn("[Candy][ototest] senaryo dinleyicisi takildi");
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy][ototest] senaryo dinleyicisi takilamadi: {}", t.toString());
        }
    }
}
