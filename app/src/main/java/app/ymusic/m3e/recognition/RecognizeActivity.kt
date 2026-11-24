@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.ymusic.m3e.recognition

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import app.ymusic.m3e.MainActivity
import app.ymusic.m3e.ui.RecognitionContent
import app.ymusic.m3e.ui.YMusicTheme

/**
 * Прозрачное окно распознавания: его открывает плитка из шторки уведомлений (и кнопка «Распознать» в приложении
 * при необходимости). Показывает ту же панель, что и в приложении, снизу поверх текущего экрана.
 */
class RecognizeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Новое открытие — чистое состояние (старый результат не показываем).
        if (savedInstanceState == null && !Recognizer.busy) Recognizer.reset()
        setContent {
            YMusicTheme {
                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    RecognitionContent(
                        autoOpen = true,
                        onClose = { finish() },
                        onOpenInApp = { r ->
                            startActivity(
                                Intent(this@RecognizeActivity, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_SEARCH_QUERY, r.query)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            )
                            finish()
                        },
                    )
                }
            }
        }
    }
}
