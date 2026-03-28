package com.sentinoid.shield

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import com.sentinoid.app.ui.FPMDashboardActivity
import com.sentinoid.app.ui.RecoveryActivity
import com.sentinoid.app.ui.SecurityDashboardActivity
import com.sentinoid.shield.ui.theme.sentinoidTheme
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

class MainActivity : ComponentActivity() {
    private var db: SovereignDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        val passphrase = KeyManager.getOrCreatePassphrase(this)

        SQLiteDatabase.loadLibs(this)

        val factory = SupportFactory(passphrase)

        db =
            Room.databaseBuilder(
                applicationContext,
                SovereignDatabase::class.java,
                "sentinoid_ledger.db",
            )
                .openHelperFactory(SupportFactory(passphrase))
                .openHelperFactory(factory)
                .build()

        enableEdgeToEdge()
        setContent {
            sentinoidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    mainScreen(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        db?.close()
    }
}

@Composable
fun mainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Sentinoid",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
        )

        Text(
            text = "Air-Gapped Security",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        mainScreenButton(
            text = "Security Dashboard",
            onClick = {
                val intent = Intent(context, SecurityDashboardActivity::class.java)
                context.startActivity(intent)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        mainScreenButton(
            text = "Feature Permission Manager",
            onClick = {
                val intent = Intent(context, FPMDashboardActivity::class.java)
                context.startActivity(intent)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        mainScreenButton(
            text = "Recovery",
            onClick = {
                val intent = Intent(context, RecoveryActivity::class.java)
                context.startActivity(intent)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        mainScreenButton(
            text = "Watchdog Status",
            onClick = {
                // TODO: Navigate to Watchdog Status activity
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        mainScreenButton(
            text = "Honeypot Status",
            onClick = {
                // TODO: Navigate to Honeypot Status activity
            },
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun mainScreenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
