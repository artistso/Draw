package com.animationstudio

import android.app.Application
import androidx.room.Room
import com.animationstudio.data.AppDatabase
import com.animationstudio.ai.AIModelManager
import com.animationstudio.engine.AnimationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnimationApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var aiModelManager: AIModelManager
        private set

    lateinit var animationEngine: AnimationEngine
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "animation_studio.db"
        )
            .setJournalMode(Room.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()

        aiModelManager = AIModelManager(this)
        animationEngine = AnimationEngine()

        applicationScope.launch {
            aiModelManager.initializeModels()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        aiModelManager.close()
    }

    companion object {
        lateinit var instance: AnimationApp
            private set
    }
}
