package com.chenyehui.bible

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chenyehui.bible.ui.BibleApp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    /**
     * -1 = volume up (previous page), 1 = volume down (next page).
     * The volume keys belong to the reader while the app is in front; they do
     * not change the system volume.
     */
    private val volumeKeys = MutableSharedFlow<Int>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The reader is a full-screen text surface. Without a refresh-rate
        // preference, some OEMs keep ordinary app windows at 60 Hz even on
        // 120/144 Hz panels, which makes page turns visibly choppier than
        // other apps. Ask for the panel's highest rate; this is only a hint,
        // so devices that ignore it simply keep their normal refresh rate.
        @Suppress("DEPRECATION")
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        }
        val maxRefreshRate = currentDisplay.supportedModes
            .asSequence()
            .map { it.refreshRate }
            .filter { it.isFinite() && it > 0f }
            .maxOrNull() ?: 60f
        window.attributes = window.attributes.apply {
            preferredRefreshRate = maxRefreshRate
        }
        setContent {
            BibleApp(volumeKeys = volumeKeys)
        }
    }

    override fun onResume() {
        super.onResume()
        // The reader has no text fields. If the previous app left its IME open,
        // do not let that software keyboard resize/reflow the reader.
        window.decorView.post {
            getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> -1
            KeyEvent.KEYCODE_VOLUME_DOWN -> 1
            else -> 0
        }
        if (direction != 0) {
            if (event.repeatCount == 0) {
                volumeKeys.tryEmit(direction)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
