package com.pawan.hisaab.data.seed

import java.util.Locale

/**
 * Well-known Indian merchants mapped to a default category, so "Zomato" or "Zepto"
 * never has to be sorted by hand. Order matters: more specific brands come before
 * the generic ones they contain (Instamart before Swiggy, JioMart before Jio).
 * Category names must match [DefaultCategories].
 */
object KnownMerchants {
    private val directory: List<Pair<String, List<String>>> = listOf(
        "Groceries" to listOf(
            "instamart", "zepto", "blinkit", "grofers", "bigbasket", "bbnow", "jiomart", "dmart", "more retail",
            "reliance fresh", "smart bazaar", "star bazaar", "spencers", "ratnadeep", "natures basket", "freshtohome", "licious",
        ),
        "Online Food orders" to listOf("zomato", "swiggy", "eatsure", "faasos", "dominos", "pizzahut", "box8", "behrouz"),
        "Dining Out" to listOf("starbucks", "mcdonald", "burgerking", "burger king", "kfc", "haldiram", "chaayos", "third wave", "barbeque nation"),
        "Drinks" to listOf("liquor", "wines", "brewery", "living liquidz", "tonique"),
        "Video / DVD / Movies" to listOf("bookmyshow", "pvr", "inox", "cinepolis"),
        "OTT" to listOf("netflix", "hotstar", "jiocinema", "primevideo", "prime video", "spotify", "youtube", "sonyliv", "zee5"),
        "Phone" to listOf("airtel", "jio", "vodafone", "bsnl"),
        "Internet" to listOf("act fibernet", "actfibernet", "hathway", "excitel"),
        "Electricity" to listOf(
            "electricity", "bescom", "tneb", "tangedco", "msedcl", "bses", "tatapower", "tata power", "adani electricity",
            "kseb", "cesc", "tsspdcl", "apspdcl", "uppcl", "pspcl",
        ),
        "Gas / Oil" to listOf("indane", "hp gas", "hpgas", "bharatgas", "bharat gas", "mahanagar gas"),
        "Fuel" to listOf(
            "hpcl", "iocl", "bpcl", "indianoil", "indian oil", "hindustan petroleum", "bharat petroleum", "nayara", "shell", "petrol", "fuel",
        ),
        "Cab / Auto" to listOf("uber", "ola", "rapido", "namma yatri", "nammayatri", "blusmart", "meru"),
        "Public Transportation" to listOf("irctc", "redbus", "abhibus", "metro", "bmrcl", "dmrc", "mmrcl", "ksrtc", "tsrtc", "msrtc", "apsrtc"),
        "Medicine / Prescriptions" to listOf("pharmeasy", "netmeds", "1mg", "apollo pharmacy", "medplus", "pharmacy", "chemist"),
        "Doctors / Dentist Visits" to listOf("hospital", "clinic", "diagnostic", "practo"),
        "Gym Membership" to listOf("cultfit", "cult fit", "curefit"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq", "decathlon", "croma", "reliance digital", "ikea"),
    )

    /** Category name for a payee, or null if it isn't a merchant we recognise. */
    fun categoryFor(merchantKey: String, merchantRaw: String = ""): String? {
        val texts = listOf(merchantKey, clean(merchantRaw)).filter { it.isNotBlank() }
        for ((category, keywords) in directory) {
            if (keywords.any { kw -> texts.any { matches(it, kw) } }) return category
        }
        return null
    }

    private fun clean(raw: String): String =
        raw.lowercase(Locale.ROOT).substringBefore('@').replace(Regex("[^a-z0-9]+"), " ").trim()

    /**
     * Long keywords match anywhere (VPAs glue words together: "swiggyinstamart");
     * short ones must start a word, so "ola" matches "olacabs" but not "cocacola".
     */
    private fun matches(text: String, keyword: String): Boolean {
        val compactText = text.replace(" ", "")
        val compactKw = keyword.replace(" ", "")
        if (compactKw.length >= 6 && compactText.contains(compactKw)) return true
        if (keyword.contains(' ') && " $text ".contains(" $keyword")) return true
        return text.split(' ').any { it.startsWith(compactKw) }
    }
}
