package com.pawan.hisaab.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CategoryGroup::class, Category::class, Txn::class, MonthlyBudget::class, MerchantRule::class],
    version = 2,
    exportSchema = true,
    // 2: shared bills (paybackForId, sharePaise).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun budgetDao(): BudgetDao
    abstract fun merchantRuleDao(): MerchantRuleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "hisaab.db").build()
    }
}
