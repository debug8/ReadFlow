package net.readflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.readflow.ui.MainScreen
import net.readflow.ui.theme.ReadFlowTheme

/**
 * Єдина Activity застосунку: увесь інтерфейс — один екран (див. SPEC_ANDROID, розділ 3),
 * додаткове показується нижніми аркушами, а не переходами.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ReadFlowTheme {
                MainScreen()
            }
        }
    }
}
