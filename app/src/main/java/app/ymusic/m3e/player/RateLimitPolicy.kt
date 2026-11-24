package app.ymusic.m3e.player

import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import app.ymusic.m3e.data.AppLog

/**
 * Обычная политика ExoPlayer, но при коде 429 («слишком много запросов») плеер сам не повторяет запрос:
 * его повторы лишь расходуют лимит SoundCloud. Ожидание и повтор делает приложение (см. AppViewModel.onPlayerError).
 */
class RateLimitPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val e = loadErrorInfo.exception
        if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode == 429) {
            val ra = e.headerFields.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value?.firstOrNull()
            AppLog.w("Player", "429 от ${e.dataSpec.uri.host}, Retry-After=${ra ?: "нет"}")
            return androidx.media3.common.C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }
}
