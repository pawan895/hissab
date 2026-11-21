package com.pawan.hisaab.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class GroupKind { INCOME, SAVINGS, EXPENSE }

enum class Direction { DEBIT, CREDIT }

enum class TxnSource { SMS, MANUAL, CASH, IMPORT }

@Serializable
@Entity(tableName = "category_groups")
data class CategoryGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: GroupKind,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val emoji: String = "•",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

/** A single money movement. Amounts are stored in paise to avoid floating point errors. */
@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["smsHash"], unique = true),
        Index("timestamp"),
        Index("categoryId"),
        Index("merchantKey"),
        Index("refNo"),
        Index("paybackForId"),
    ],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val direction: Direction,
    val timestamp: Long,
    val merchantRaw: String,
    val merchantKey: String,
    val categoryId: Long? = null,
    val note: String = "",
    val bank: String = "",
    val accountLast4: String = "",
    val refNo: String = "",
    val source: TxnSource,
    val smsBody: String? = null,
    val smsHash: String? = null,
    val balancePaise: Long? = null,
    /** True when the generic parser guessed the fields; shown with a "verify" badge. */
    val needsReview: Boolean = false,
    /**
     * On a credit: the bill this money pays back, e.g. a friend sending their share of a
     * dinner you paid for. Such a credit is not income; it lowers what the bill cost you.
     */
    val paybackForId: Long? = null,
    /** On a debit you paid for others too: your own share. Null when the bill isn't shared. */
    val sharePaise: Long? = null,
)

@Serializable
@Entity(tableName = "monthly_budgets", primaryKeys = ["month", "categoryId"])
data class MonthlyBudget(
    /** yyyy-MM */
    val month: String,
    val categoryId: Long,
    val projectedPaise: Long,
)

/** Learned mapping from a merchant to the category the user keeps picking for it. */
@Serializable
@Entity(tableName = "merchant_rules", primaryKeys = ["merchantKey", "direction"])
data class MerchantRule(
    val merchantKey: String,
    val direction: Direction,
    val categoryId: Long,
    val hitCount: Int,
)
