package com.technavious.om15

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.technavious.om15.data.db.AppDatabase
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.ui.navigation.OM15NavHost
import com.technavious.om15.ui.theme.OM15Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = TestRepository(db)

        setContent {
            OM15Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    OM15NavHost(
                        navController = navController,
                        repository = repository
                    )
                }
            }
        }
    }
}
