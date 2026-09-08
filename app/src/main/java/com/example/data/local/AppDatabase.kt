package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SmsQueueEntity::class,
        CachedOrderEntity::class,
        CachedPaymentEntity::class,
        AppealEntity::class,
        MerchantProfileEntity::class,
        DeviceInfoEntity::class,
        SupabaseProfileEntity::class,
        MfsPatternEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        LedgerTransactionEntity::class,
        PosSaleEntity::class,
        ProductItemEntity::class,
        ProductVariantEntity::class,
        StockTransactionEntity::class,
        ExpenseEntity::class,
        BusinessLoanEntity::class,
        DpsAccountEntity::class,
        FinanceInstallmentEntity::class,
        MerchantNotificationEntity::class,
        BusinessAnalyticsEntity::class,
        EmployeeEntity::class,
        MerchantNumberEntity::class,
        PaymentFormCacheEntity::class,
        FormSubmissionCacheEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `employees` (`id` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `name` TEXT NOT NULL, `designation` TEXT NOT NULL, `role` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, `department` TEXT NOT NULL, `status` TEXT NOT NULL, `avatarUrl` TEXT, `permissionsJson` TEXT NOT NULL, `joinedDate` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_employees_merchantId` ON `employees` (`merchantId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_numbers` (`number` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `method` TEXT NOT NULL, `accountType` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`number`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_numbers_merchantId` ON `merchant_numbers` (`merchantId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `payment_form_cache` (`id` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isDirty` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_form_cache_merchantId` ON `payment_form_cache` (`merchantId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `form_submission_cache` (`id` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `formId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `submittedAt` INTEGER NOT NULL, `isDirty` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_form_submission_cache_merchantId` ON `form_submission_cache` (`merchantId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `supabase_profiles` ADD COLUMN `authRefreshToken` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `supabase_profiles` ADD COLUMN `authTokenExpiresAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val defaultMerchant = "00000000-0000-0000-0000-000000000001"
                db.execSQL("ALTER TABLE `sms_queue` ADD COLUMN `merchantId` TEXT NOT NULL DEFAULT '$defaultMerchant'")
                db.execSQL("ALTER TABLE `cached_orders` ADD COLUMN `merchantId` TEXT NOT NULL DEFAULT '$defaultMerchant'")
                db.execSQL("ALTER TABLE `cached_payments` ADD COLUMN `merchantId` TEXT NOT NULL DEFAULT '$defaultMerchant'")
                db.execSQL("ALTER TABLE `appeals` ADD COLUMN `merchantId` TEXT NOT NULL DEFAULT '$defaultMerchant'")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `merchantId` TEXT NOT NULL DEFAULT '$defaultMerchant'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_queue_merchantId` ON `sms_queue` (`merchantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_orders_merchantId` ON `cached_orders` (`merchantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_payments_merchantId` ON `cached_payments` (`merchantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_appeals_merchantId` ON `appeals` (`merchantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_merchantId` ON `devices` (`merchantId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_numbers_new` (`number` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `method` TEXT NOT NULL, `accountType` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`merchantId`, `number`))")
                db.execSQL("INSERT OR REPLACE INTO `merchant_numbers_new` (`number`,`merchantId`,`method`,`accountType`,`isActive`,`isDefault`,`updatedAt`) SELECT `number`,`merchantId`,`method`,`accountType`,`isActive`,`isDefault`,`updatedAt` FROM `merchant_numbers`")
                db.execSQL("DROP TABLE `merchant_numbers`")
                db.execSQL("ALTER TABLE `merchant_numbers_new` RENAME TO `merchant_numbers`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_numbers_merchantId` ON `merchant_numbers` (`merchantId`)")

                listOf(
                    "customers", "suppliers", "ledger_transactions", "pos_sales", "products",
                    "product_variants", "stock_transactions", "expenses", "loans", "business_analytics"
                ).forEach { table ->
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_merchantId` ON `$table` (`merchantId`)")
                }
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `providerName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `accountReference` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `startDate` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `loans` ADD COLUMN `isSynced` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `dps_accounts` (`id` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `providerName` TEXT NOT NULL, `accountReference` TEXT NOT NULL, `monthlyDeposit` REAL NOT NULL, `interestRate` REAL NOT NULL, `durationMonths` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `maturityDate` INTEGER NOT NULL, `status` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dps_accounts_merchantId` ON `dps_accounts` (`merchantId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dps_accounts_merchantId_accountReference` ON `dps_accounts` (`merchantId`, `accountReference`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `finance_installments` (`id` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `accountType` TEXT NOT NULL, `accountId` TEXT NOT NULL, `installmentNumber` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `principalAmount` REAL NOT NULL, `interestAmount` REAL NOT NULL, `totalAmount` REAL NOT NULL, `status` TEXT NOT NULL, `paidAt` INTEGER, `paymentMethod` TEXT, `paymentReference` TEXT, `isSynced` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_finance_installments_merchantId` ON `finance_installments` (`merchantId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_finance_installments_merchantId_accountType_accountId_installmentNumber` ON `finance_installments` (`merchantId`, `accountType`, `accountId`, `installmentNumber`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_finance_installments_merchantId_paymentReference` ON `finance_installments` (`merchantId`, `paymentReference`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_notifications` (`id` TEXT NOT NULL, `merchantId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `severity` TEXT NOT NULL, `entityType` TEXT, `entityId` TEXT, `createdAt` INTEGER NOT NULL, `readAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_notifications_merchantId` ON `merchant_notifications` (`merchantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_notifications_createdAt` ON `merchant_notifications` (`createdAt`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `merchant_numbers` ADD COLUMN `qrCodeUrl` TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "swapnopay_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
