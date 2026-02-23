package com.sentinoid.shield

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sentinoid.shield.ui.theme.SentinoidTheme
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import androidx.room.Room

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This will prevent screenshot and screen recording!
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // 1. Get our hardware-backed passphrase
        val passphrase = KeyManager.getOrCreatePassphrase(this)

        // 2. Initialize SQLCipher libraries (Required!)
        SQLiteDatabase.loadLibs(this)

        // 3. Create the "Lock" factory
        val factory = SupportFactory(passphrase)

        // 4. Build the Encrypted Database
        val db = Room.databaseBuilder(
            applicationContext,
            SovereignDatabase::class.java,
            "sentinoid_ledger.db"
        )
            .openHelperFactory(factory) // This line applies the encryption!
            .build()

        enableEdgeToEdge()
        setContent {
            SentinoidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SentinoidTheme {
        Greeting("Android")
    }
}
