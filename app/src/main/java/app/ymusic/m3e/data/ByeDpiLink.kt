package app.ymusic.m3e.data

import android.content.Context
import android.content.Intent
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Связка с приложением ByeByeDPI (io.github.romanvht.byedpi). Управляем им через его `ToggleActivity`:
 *  • `strategy` — командная строка ciadpi, которая записывается в настройки ByeByeDPI («Командная строка»);
 *  • `only_start` — только запустить, если он остановлен.
 * Если ByeByeDPI уже запущен, а `strategy` отличается от текущей, он перезапускается с новой; если совпадает — переключатель
 * его остановил бы, поэтому после каждой отправки проверяем порт и при необходимости запускаем ещё раз.
 */
object ByeDpiLink {
    const val PKG = "io.github.romanvht.byedpi"
    const val PORT = 1080
    private const val TAG = "ByeDpiLink"

    /** SNI для стратегий с «-n {sni}» (подставляется так же, как в проверке самого ByeByeDPI). */
    private const val FAKE_SNI = "google.com"

    val upstream = ScUpstream(ScProxyType.SOCKS5, "127.0.0.1", PORT)

    fun installed(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(PKG, 0) }.isSuccess

    /** Слушает ли кто-нибудь порт ByeByeDPI на этом телефоне. */
    fun portOpen(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 300) }
        true
    } catch (e: Exception) { false }

    private fun toggle(ctx: Context, strategy: String?, onlyStart: Boolean): Boolean = try {
        val i = Intent().setClassName(PKG, "$PKG.activities.ToggleActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (strategy != null) i.putExtra("strategy", strategy.replace("{sni}", FAKE_SNI))
        if (onlyStart) i.putExtra("only_start", true)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        AppLog.w(TAG, "не удалось обратиться к ByeByeDPI: ${e.message}")
        false
    }

    private fun waitPort(ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (portOpen()) return true
            Thread.sleep(150)
        }
        return portOpen()
    }

    /** Запускает ByeByeDPI (если нужно) с командной строкой [cmd]. true — порт SOCKS5 открыт. Блокирует поток. */
    fun applyBlocking(ctx: Context, cmd: String): Boolean {
        val running = portOpen()
        if (!toggle(ctx, cmd, onlyStart = !running)) return false
        if (running) Thread.sleep(1200) // перезапуск: старый процесс уходит, новый поднимается
        if (waitPort(3500)) return true
        // Совпавшая стратегия могла остановить сервис, а запуск мог не успеть: просим запустить ещё раз.
        toggle(ctx, null, onlyStart = true)
        return waitPort(4000)
    }

    /** Просто запустить ByeByeDPI с тем, что в нём настроено. */
    fun start(ctx: Context): Boolean = toggle(ctx, null, onlyStart = true)

    class ProbeResult(val found: List<ByeStrategy>, val checked: Int, val total: Int, val startFailed: Boolean, val cancelled: Boolean)

    /** Готовые стратегии из списка проверки самого ByeByeDPI (assets/proxytest_strategies.list, 1.8.2). */
    val candidates: List<String> = CANDIDATES_RAW.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Перебирает [candidates]: запускает ByeByeDPI с очередной стратегией и открывает через него API и CDN SoundCloud
     * ([ScProxy.quickCheck]). Рабочей считается стратегия, с которой открылись все адреса. Останавливается, когда нашлось
     * [maxFound] стратегий (они отсортированы от самой быстрой) или кончился список. Блокирует поток: вызывать не из главного.
     */
    fun probe(ctx: Context, maxFound: Int, cancelled: () -> Boolean, onProgress: (checked: Int, total: Int, found: Int) -> Unit): ProbeResult {
        val found = ArrayList<ByeStrategy>()
        var checked = 0
        var startFailed = false
        ScProxy.withUpstream(upstream) {
            for (cmd in candidates) {
                if (cancelled()) break
                onProgress(checked, candidates.size, found.size)
                if (!applyBlocking(ctx, cmd)) { startFailed = true; break }
                ScProxy.quickCheck()?.let { found.add(ByeStrategy(cmd, it)) }
                checked++
                if (found.size >= maxFound) break
            }
        }
        onProgress(checked, candidates.size, found.size)
        found.sortBy { it.ms }
        // Оставляем ByeByeDPI запущенным с лучшей стратегией.
        if (found.isNotEmpty() && !cancelled()) applyBlocking(ctx, found[0].cmd)
        return ProbeResult(found, checked, candidates.size, startFailed, cancelled())
    }

    private const val CANDIDATES_RAW = """
-f-200 -Qr -s3:5+sm -a1 -At,r,s,c -d1 -s4+sm -s8+s -f-300 -d6+s -a1 -At,r,s,c -o2 -f-30 -At,r,s,c -r5 -Mh -r6+s -f-250 -s2:7+s -s3:6+sm -a1 -At,r,s,c -s3:5+sm -s6+s -s7:9+s -q30+sm -a1
-d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -a1 -At,r,s,c -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1
-q2 -s2 -s3+s -r3 -s4 -r4 -s5+s -r5+s -s6 -s7+s -r8 -s9+s -Qr -Mh,d,r -a1 -At,r,s,c -s2+s -r2 -d2 -s3 -r3 -r4 -s4 -d5+s -r5 -d6 -s7+s -d7 -a1
-o1 -a1 -At,r,s,c -s1 -s5+s -s10+s -s15+s -s20+s -r1+s -a1 -At,r,s,c -s1 -s5+s -s10+s -s15+s -s20+s -a1
-n {sni} -Qr -f-204 -s1+sm -a1 -At,r,s,c -d1 -s3+s -s5+s -a1 -At,r,s,c -o2 -f-43 -a1 -At,r,s,c -r5 -Mh -s1+s -s3+sm -a1
-n {sni} -Qr -f-205 -a1 -At,r,s,c -s1:3+sm -a1 -At,r,s,c -s5:8+sm -a1 -At,r,s,c -d3 -q7 -o2 -f-43 -f-85 -f-165 -r5 -Mh -a1
-d1+s -s50+s -a1 -At,r,s,c -f20 -r2+s -a1 -At,r,s,c -d2 -s1+s -s5+s -s10+s -s15+s -s25+s -s35+s -s50+s -s60+s -a1
-o1 -a1 -At,r,s,c -f-1 -a1 -At,r,s,c -d1+sm -a1 -At,r,s,c -n {sni} -Qr -f1 -d1+sm -s1+sm -S -a1
-d1 -q1 -a1 -At,r,s,c -s5 -o1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1
-f1+nm -t6 -a1 -At,r,s,c -s1+sm -a1 -At,r,s,c -s5+sm -a1 -At,r,s,c -d3 -q7 -r6 -Mh -a1
-d1 -s1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1
-d1 -s1+s -d1+s -s3+s -d6+s -s12+s -d14+s -s20+s -d24+s -s30+s -a1
-o1 -a1 -At,r,s,c -f-1 -a1 -At,r,s,c -o1 -a1 -At,r,s,c -r1+s -f-1 -t6 -a1
-d1 -s1+s -s3+s -s6+s -s9+s -s12+s -s15+s -s20+s -s30+s -a1
-d1 -d3+s -s6+s -d6+s -s7+s -d8+s -s10+s -a1 -t12 -At,r,s,c -r3
-f1 -t5 -n {sni} -q3+h -Qr -f2 -q1 -r1+s -t15 -q1 -o2 -a1
-n {sni} -d2:5:2+h -f-3 -r2+sm -o2 -o50+s -r2+s -f-4 -a1
-f-1 -Qr -a1 -At,r,s,c -r1+s -d8+s -a1
-r-1+s -o20+sm -s3:7+sm -d5:3+sm -f300+s -Qr -f-1 -a1
-o2 -O4 -a1 -At,r,s,c -s5 -o1+s -r20+s -a1
-o1 -r-5+se -a1 -At,r,s,c -d1 -n {sni} -Qr -f-1 -a1
--fake -1 -a1
-n {sni} -Qr -f6+nr -d2 -d11 -f9+hm -o3 -t7 -a1
-r5+s -s25+s -a1 -At,r,s,c -s50 -r5+s -s50+s -a1
-d1 -d3+s -s6+s -d9+s -s20+s -d25+s -s30+s -a1
-d9+s -q20+s -s25+s -t5 -a1 -At,r,s,c -r1+h -a1
-q1+s -s29+s -s30+s -s14+s -o5+s -f-1 -S -a1
-d1 -s1+s -r1+s -e1 -m1 -o1+s -f-1 -t2 -a1
-d1 -o1 -a1 -At,r,s,c -o1 -a1 -At,r,s,c -f-1 -r1+s -a1
-d1 -s4 -d8 -s1+s -d5+s -s10+s -d20+s -a1
-f-1 -n {sni} -Qr -r3 -t4 -a1
-d5+sm -t4 -a1
-o1 -a1 -At,r,s,c -q1 -a1 -At,r,s,c -f-1 -r1+s -a1
-q1 -a1 -At,r,s,c -o1 -a1 -At,r,s,c -f-1 -r1+s -a1
-s4+s -r9+s -a1
-o1 -r1+s -s1+s -d3+s -a1
-q1+s -s29+s -o5+s -f-1 -S -a1
-n {sni} -Qr -m2 -f-1 -a1
-d1 -s1+s -r1+s -f-1 -t8 -a1
-o1 -a1 -At,r,s,c -f1+nm -t6 -a1
-n {sni} -Qr -f-1 -r1+s -a1
-n {sni} -Qr -d1 -f-1 -a1
-s1 -d3+s -a1 -At,r,s,c -r1+s -a1
-f-1 -t8 -n {sni} -a1
-f64+se -n {sni} -t5 -a1
-o1 -a1 -At,r,s,c -d1 -a1
-d1+s -r5 -a1
-r8 -o2 -s7 -q4+s -a1
-o1 -f-1 -r-5+se -a1
-d6+s -q4+hm -o2 -a1
-s5+s -s35+s -m4 -a1
-f-1+sm -t7 -m2 -a1
-o1 -r-5+se -a1
-o1+s -d3+s -a1
-o1 -s4 -s6 -a1
-q1 -r25+s -a1
-d1 -s3+s -a1
-o3 -d7 -a1
-d7 -a1
"""
}
