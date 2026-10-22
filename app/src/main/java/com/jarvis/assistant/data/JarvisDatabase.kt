package com.jarvis.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jarvis.assistant.data.dao.AliasDao
import com.jarvis.assistant.data.entity.AliasEntity

@Database(entities = [AliasEntity::class], version = 1, exportSchema = false)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun aliasDao(): AliasDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        private const val DATABASE_NAME = "jarvis_memory.db"

        fun getDatabase(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
