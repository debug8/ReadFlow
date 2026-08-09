package net.readflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.readflow.ui.ReadFlowApp

/**
 * Єдина Activity застосунку: увесь інтерфейс — один екран (див. SPEC_ANDROID, розділ 3),
 * додаткове показується нижніми аркушами, а не переходами.
 *
 * Тема застосовується всередині [ReadFlowApp], а не тут: її вибір лежить у
 * налаштуваннях, а їх читає ViewModel.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ReadFlowApp()
        }
    }
}
