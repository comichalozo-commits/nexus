package de.nexus.agent.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import de.nexus.agent.core.common.Constants

@Database(
    entities = [
        MessageEntity::class,
        ToolEntity::class,
        MemoryFactEntity::class,
        SkillEntity::class,
        ScheduledJobEntity::class,
        ConversationEntity::class
    ],
    version = Constants.DATABASE_VERSION,
    exportSchema = true
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun toolDao(): ToolDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun skillDao(): SkillDao
    abstract fun scheduledJobDao(): ScheduledJobDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: NexusDatabase? = null

        fun getInstance(context: Context): NexusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexusDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
