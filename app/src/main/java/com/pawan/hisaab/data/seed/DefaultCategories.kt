package com.pawan.hisaab.data.seed

import com.pawan.hisaab.data.db.GroupKind

/**
 * Groups and categories mirror the "Monthly Budget" Google Sheet so a CSV import
 * lines up by name. A few everyday categories (Drinks, Cab / Auto, …) are added.
 */
object DefaultCategories {
    data class SeedGroup(val name: String, val kind: GroupKind, val categories: List<Pair<String, String>>)

    val groups = listOf(
        SeedGroup(
            "Income", GroupKind.INCOME,
            listOf(
                "Salary / Wages" to "💼", "Interest Income" to "🏦", "Dividends" to "📈",
                "Refunds / Reimbursements" to "↩️", "Business" to "🧾", "Pension" to "🧓",
                "Transfer from Self" to "🔁", "Misc." to "💰",
            ),
        ),
        SeedGroup(
            "Savings", GroupKind.SAVINGS,
            listOf(
                "Emergency Fund" to "🛟", "Transfer to Savings" to "🏦", "Retirement(401K, IRA)" to "🏖️",
                "Investments" to "📊", "Education" to "🎓", "Other" to "💎",
            ),
        ),
        SeedGroup(
            "Home", GroupKind.EXPENSE,
            listOf(
                "Rent" to "🏠", "Loan" to "🏛️", "Electricity" to "💡", "Gas / Oil" to "🔥",
                "Water / Sewer / Trash" to "🚰", "Phone" to "📱", "Cable / Satellite" to "📡",
                "Internet" to "🌐", "Furnishing / Appliances" to "🛋️", "Lawn / Garden" to "🌱",
                "Maintenance / Improvements" to "🔧", "Other" to "📦",
            ),
        ),
        SeedGroup(
            "Transportation", GroupKind.EXPENSE,
            listOf(
                "Car Payments" to "🚗", "Auto Insurance" to "🛡️", "Fuel" to "⛽",
                "Public Transportation" to "🚌", "Cab / Auto" to "🛺", "Repairs / Maintenance" to "🔩",
                "Registration / License" to "📄",
            ),
        ),
        SeedGroup(
            "Daily Living", GroupKind.EXPENSE,
            listOf(
                "Online Food orders" to "🛵", "Dining Out" to "🍽️", "Food & Snacks" to "🍔",
                "Drinks" to "🍺", "Groceries" to "🛒", "Smoking" to "🚬", "Clothing" to "👕",
                "Shopping" to "🛍️", "Salon / Barber" to "💈", "Gifting" to "🎁",
            ),
        ),
        SeedGroup(
            "Entertainment", GroupKind.EXPENSE,
            listOf(
                "Video / DVD / Movies" to "🎬", "Concerts / Plays" to "🎭", "Sports" to "🏏", "OTT" to "📺",
            ),
        ),
        SeedGroup(
            "Health", GroupKind.EXPENSE,
            listOf(
                "Health Insurance" to "🏥", "Gym Membership" to "🏋️", "Doctors / Dentist Visits" to "🩺",
                "Medicine / Prescriptions" to "💊", "Veterinarian" to "🐾", "Life Insurance" to "🛡️",
            ),
        ),
        SeedGroup(
            "Home & Family", GroupKind.EXPENSE,
            listOf(
                "Travel to Home" to "🚆", "Insurance" to "📑", "EB + TV Reacharge" to "🔌",
                "Family Support" to "👨‍👩‍👧", "Family Gifts" to "🎀",
            ),
        ),
        SeedGroup(
            "Other", GroupKind.EXPENSE,
            listOf("Sent to Friends" to "🤝", "Miscellaneous" to "❓"),
        ),
    )

    /** Shown in the popup before the user has any history. */
    val defaultDebitSuggestions = listOf(
        "Online Food orders", "Dining Out", "Food & Snacks", "Drinks", "Groceries", "Cab / Auto", "Shopping", "Sent to Friends",
    )
    val defaultCreditSuggestions = listOf(
        "Salary / Wages", "Refunds / Reimbursements", "Transfer from Self", "Misc.",
    )
}
