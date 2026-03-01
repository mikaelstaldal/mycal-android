package nu.staldal.mycal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nu.staldal.mycal.ui.navigation.NavGraph
import nu.staldal.mycal.ui.theme.MyCalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyCalTheme {
                NavGraph()
            }
        }
    }
}
