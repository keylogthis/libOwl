package org.thebottle.libOwl.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class, Loan::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun loanDao(): LoanDao

    companion object {
        /** v2: pages, price, rating, description, cover; read status "READ" renamed to "FINISHED". */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN pages INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN price REAL")
                db.execSQL("ALTER TABLE books ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN coverFile TEXT")
                db.execSQL("UPDATE books SET readStatus = 'FINISHED' WHERE readStatus = 'READ'")
            }
        }

        /** v3: ratings are now stored in half-star units (previously whole stars). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE books SET rating = rating * 2")
            }
        }

        /** v4: custom properties were dropped from the design; remove their unused tables. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS property_values")
                db.execSQL("DROP TABLE IF EXISTS property_definitions")
            }
        }

        /** v5: books saved from a scan without details are flagged so they can be reviewed. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN needsAttention INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "libowl.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
