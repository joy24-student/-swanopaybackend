package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate6to7() {
        var db = helper.createDatabase(TEST_DB, 6)
        db.close()
        db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate7to8() {
        var db = helper.createDatabase(TEST_DB, 7)
        db.close()
        db = helper.runMigrationsAndValidate(TEST_DB, 8, true, AppDatabase.MIGRATION_7_8)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate8to9() {
        var db = helper.createDatabase(TEST_DB, 8)
        db.close()
        db = helper.runMigrationsAndValidate(TEST_DB, 9, true, AppDatabase.MIGRATION_8_9)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate9to10() {
        var db = helper.createDatabase(TEST_DB, 9)
        db.close()
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_10)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        var db = helper.createDatabase(TEST_DB, 6)
        db.close()
        db = helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10
        )
        db.close()
    }
}
