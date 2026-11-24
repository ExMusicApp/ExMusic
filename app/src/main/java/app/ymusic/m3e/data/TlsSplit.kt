package app.ymusic.m3e.data

/**
 * Разбор TLS ClientHello и нарезка его на части, чтобы DPI не мог прочитать имя сайта (SNI) в одном пакете.
 * Само шифрование не затрагивается: сервер получает те же байты (или тот же ClientHello, разложенный по нескольким
 * TLS-записям), а сертификат по-прежнему проверяет приложение.
 *
 * Набор приёмов повторяет самые рабочие в РФ приёмы ByeByeDPI (split, tlsrec, disorder, oob).
 */
internal object TlsSplit {
    /** Сколько всего есть приёмов (см. [plan]). Номера 0..3 не менять: они сохранены в настройках. */
    const val STRATEGIES = 7

    val STRATEGY_NAMES = listOf(
        "TCP-пакеты", "TLS-записи", "мелкое дробление", "без дробления",
        "disorder", "OOB-байт", "TLS-записи и disorder",
    )

    /** Порядок перебора в автоматическом режиме: сначала самые «пробивные», «без дробления» — в конце. */
    private val AUTO_ORDER = intArrayOf(0, 4, 5, 1, 6, 2, 3)

    /**
     * Порядок перебора, начиная с приёма [first]: дальше по кругу в порядке [AUTO_ORDER].
     * Так следующий запрос после неудачи начинает с ещё не опробованного приёма.
     */
    fun autoOrder(first: Int): List<Int> {
        val i = AUTO_ORDER.indexOf(first).takeIf { it >= 0 } ?: 0
        return List(AUTO_ORDER.size) { AUTO_ORDER[(i + it) % AUTO_ORDER.size] }
    }

    /**
     * Один отправляемый серверу кусок.
     * [lowTtl] — отправить с TTL=1: пакет не дойдёт до сервера и будет переслан позже штатной повторной отправкой TCP
     * (сервер получит куски в обратном порядке, а DPI не соберёт имя сайта).
     * [urgentAfter] — сразу после куска отправить один «срочный» байт (OOB): сервер его отбрасывает, а DPI,
     * не знающий про срочные данные, видит лишний байт посреди имени сайта.
     */
    class Step(val data: ByteArray, val lowTtl: Boolean = false, val urgentAfter: Boolean = false)

    private fun u16(b: ByteArray, i: Int): Int = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)

    /**
     * Где в записи ClientHello (вместе с 5-байтовым заголовком записи) лежит имя сайта: [начало, конец).
     * null — это не ClientHello или в нём нет SNI.
     */
    fun findSni(rec: ByteArray): IntArray? {
        val len = rec.size
        if (len < 9 || rec[0].toInt() != 0x16 || rec[5].toInt() != 0x01) return null
        var p = 9 + 2 + 32 // заголовок записи + заголовок рукопожатия + версия + random
        if (p + 1 > len) return null
        p += 1 + (rec[p].toInt() and 0xff) // session id
        if (p + 2 > len) return null
        p += 2 + u16(rec, p) // наборы шифров
        if (p + 1 > len) return null
        p += 1 + (rec[p].toInt() and 0xff) // методы сжатия
        if (p + 2 > len) return null
        val extEnd = minOf(len, p + 2 + u16(rec, p))
        p += 2
        while (p + 4 <= extEnd) {
            val type = u16(rec, p)
            val l = u16(rec, p + 2)
            p += 4
            if (type == 0) {
                // server_name: длина списка (2), тип (1), длина имени (2), имя
                if (p + 5 > extEnd) return null
                val nameLen = u16(rec, p + 3)
                val s = p + 5
                if (nameLen <= 0 || s + nameLen > len) return null
                return intArrayOf(s, s + nameLen)
            }
            p += l
        }
        return null
    }

    /** Одна TLS-запись из куска полезной нагрузки [from, to) исходной записи [rec] (заголовок берём оттуда же). */
    private fun record(rec: ByteArray, from: Int, to: Int): ByteArray {
        val n = to - from
        val out = ByteArray(5 + n)
        out[0] = rec[0]; out[1] = rec[1]; out[2] = rec[2]
        out[3] = (n shr 8).toByte(); out[4] = n.toByte()
        System.arraycopy(rec, from, out, 5, n)
        return out
    }

    /**
     * Части, которые нужно отправить серверу по очереди (каждая отдельным TCP-пакетом).
     *  0 — исходная запись режется на два TCP-пакета посередине имени сайта;
     *  1 — ClientHello перекладывается в две TLS-записи, граница посередине имени;
     *  2 — три TLS-записи (до имени, первая половина имени, остальное);
     *  3 — без изменений;
     *  4 — как 0, но первый пакет уходит с TTL=1 и доходит до сервера позже второго (disorder);
     *  5 — запись режется в начале имени, между частями вставляется срочный байт (OOB);
     *  6 — как 1, но первая запись уходит с TTL=1 (TLS-записи и disorder).
     * Если SNI не нашёлся, возвращается одна часть.
     */
    fun plan(rec: ByteArray, strategy: Int): List<Step> {
        if (strategy == 3 || strategy !in 0 until STRATEGIES) return listOf(Step(rec))
        val sni = findSni(rec) ?: return listOf(Step(rec))
        val mid = sni[0] + maxOf(1, (sni[1] - sni[0]) / 2)
        return when (strategy) {
            0 -> listOf(Step(rec.copyOfRange(0, mid)), Step(rec.copyOfRange(mid, rec.size)))
            1 -> listOf(Step(record(rec, 5, mid)), Step(record(rec, mid, rec.size)))
            2 -> listOf(Step(record(rec, 5, sni[0])), Step(record(rec, sni[0], mid)), Step(record(rec, mid, rec.size)))
            4 -> listOf(Step(rec.copyOfRange(0, mid), lowTtl = true), Step(rec.copyOfRange(mid, rec.size)))
            5 -> {
                val cut = minOf(sni[0] + 1, rec.size - 1)
                listOf(Step(rec.copyOfRange(0, cut), urgentAfter = true), Step(rec.copyOfRange(cut, rec.size)))
            }
            else -> listOf(Step(record(rec, 5, mid), lowTtl = true), Step(record(rec, mid, rec.size)))
        }
    }
}
