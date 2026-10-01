package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SettingEntry::class,
        EventMapping::class,
        PicoFile::class,
        SerialLogEntry::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PicocaseDatabase : RoomDatabase() {

    abstract fun picocaseDao(): PicocaseDao

    companion object {
        @Volatile
        private var INSTANCE: PicocaseDatabase? = null

        fun getDatabase(context: Context): PicocaseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PicocaseDatabase::class.java,
                    "picocase_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
