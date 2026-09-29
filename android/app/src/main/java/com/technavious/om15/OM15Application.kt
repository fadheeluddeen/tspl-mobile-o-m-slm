package com.technavious.om15

import android.app.Application
import com.technavious.om15.data.db.AppDatabase

class OM15Application : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
