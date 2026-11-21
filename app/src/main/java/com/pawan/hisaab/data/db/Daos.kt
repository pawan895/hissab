package com.pawan.hisaab.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category_groups ORDER BY sortOrder, id")
    fun groupsFlow(): Flow<List<CategoryGroup>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun categoriesFlow(): Flow<List<Category>>

    @Query("SELECT * FROM category_groups ORDER BY sortOrder, id")
    suspend fun allGroups(): List<CategoryGroup>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun allCategories(): List<Category>

    @Query("SELECT COUNT(*) FROM category_groups")
    suspend fun countGroups(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: Long): Category?

    @Insert
    suspend fun insertGroup(group: CategoryGroup): Long

    @Insert
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateGroup(group: CategoryGroup)

    @Update
    suspend fun updateCategory(category: Category)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<CategoryGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<Category>)
}

data class CategoryCount(val categoryId: Long, val c: Int)

/** A payee seen before, with the category used most recently. */
data class MerchantSuggestion(
    val merchantRaw: String,
    val merchantKey: String,
    val categoryId: Long?,
    val direction: Direction,
    val ts: Long,
)

@Dao
interface TxnDao {
    /** Returns -1 when the SMS hash already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: Txn): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txns: List<Txn>)

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): Txn?

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun between(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun all(): List<Txn>

    @Query("SELECT COUNT(*) FROM transactions WHERE refNo = :refNo AND amountPaise = :amountPaise")
    suspend fun countByRef(refNo: String, amountPaise: Long): Int

    // A payback has no category of its own (it belongs to its bill), so it is never "to sort".
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId IS NULL AND paybackForId IS NULL")
    fun uncategorizedCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL AND paybackForId IS NULL")
    suspend fun uncategorized(): List<Txn>

    @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :hash")
    suspend fun countByHash(hash: String): Int

    /** A hand-added payment (no SMS yet) of the same amount close in time: the SMS that just arrived is probably for it. */
    @Query(
        """SELECT * FROM transactions
           WHERE source = 'MANUAL' AND smsHash IS NULL AND smsBody IS NULL
             AND amountPaise = :amountPaise AND direction = :direction AND timestamp BETWEEN :from AND :to
           ORDER BY ABS(timestamp - :around) LIMIT 1""",
    )
    suspend fun findManualMatch(amountPaise: Long, direction: Direction, from: Long, to: Long, around: Long): Txn?

    /** SQLite returns the other columns from the row holding MAX(timestamp), i.e. the latest use. */
    @Query(
        """SELECT merchantRaw, merchantKey, categoryId, direction, MAX(timestamp) AS ts FROM transactions
           WHERE source != 'IMPORT' AND merchantKey NOT IN ('unknown', 'manual entry', 'cash')
           GROUP BY merchantKey, direction ORDER BY ts DESC LIMIT 300""",
    )
    fun merchantSuggestions(): Flow<List<MerchantSuggestion>>

    @Query(
        """SELECT * FROM transactions
           WHERE categoryId IS NULL AND paybackForId IS NULL AND merchantKey = :merchantKey AND direction = :direction""",
    )
    suspend fun uncategorizedForMerchant(merchantKey: String, direction: Direction): List<Txn>

    @Query(
        """SELECT categoryId, COUNT(*) AS c FROM transactions
           WHERE categoryId IS NOT NULL AND direction = :direction AND source != 'IMPORT'
           GROUP BY categoryId ORDER BY c DESC LIMIT :limit""",
    )
    suspend fun topCategories(direction: Direction, limit: Int): List<CategoryCount>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun listBetween(from: Long, to: Long): List<Txn>

    // ---------------- shared bills ----------------

    /** Every credit that pays back a bill. */
    @Query("SELECT * FROM transactions WHERE paybackForId IS NOT NULL ORDER BY timestamp")
    fun paybacks(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE paybackForId IS NOT NULL ORDER BY timestamp")
    suspend fun allPaybacks(): List<Txn>

    @Query("SELECT * FROM transactions WHERE paybackForId = :billId ORDER BY timestamp")
    suspend fun paybacksFor(billId: Long): List<Txn>

    /** Bills marked as shared, or that someone has paid back. */
    @Query(
        """SELECT * FROM transactions
           WHERE sharePaise IS NOT NULL OR id IN (SELECT paybackForId FROM transactions WHERE paybackForId IS NOT NULL)
           ORDER BY timestamp DESC""",
    )
    fun bills(): Flow<List<Txn>>

    @Query("SELECT COUNT(*) FROM transactions WHERE merchantKey = :merchantKey AND paybackForId IS NOT NULL")
    suspend fun paybackCountFrom(merchantKey: String): Int

    @Query("UPDATE transactions SET paybackForId = NULL WHERE paybackForId = :billId")
    suspend fun unlinkPaybacks(billId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM monthly_budgets WHERE month = :month")
    fun forMonthFlow(month: String): Flow<List<MonthlyBudget>>

    @Query("SELECT * FROM monthly_budgets WHERE month = :month")
    suspend fun forMonth(month: String): List<MonthlyBudget>

    @Query("SELECT MAX(month) FROM monthly_budgets WHERE month < :month")
    suspend fun latestMonthBefore(month: String): String?

    @Upsert
    suspend fun upsert(budget: MonthlyBudget)

    @Upsert
    suspend fun upsertAll(budgets: List<MonthlyBudget>)

    @Query("SELECT * FROM monthly_budgets")
    suspend fun all(): List<MonthlyBudget>
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :merchantKey AND direction = :direction")
    suspend fun get(merchantKey: String, direction: Direction): MerchantRule?

    @Upsert
    suspend fun upsert(rule: MerchantRule)

    @Upsert
    suspend fun upsertAll(rules: List<MerchantRule>)

    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRule>
}
