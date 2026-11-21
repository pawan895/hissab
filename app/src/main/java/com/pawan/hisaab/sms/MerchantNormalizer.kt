package com.pawan.hisaab.sms

/**
 * Turns whatever the bank printed as payee into a stable key, so that
 * "zomatoltd32.rzp@hdfcbank" and "ZOMATO" both become "zomato".
 */
object MerchantNormalizer {
    private val gatewaySuffix = Regex(
        """\.(rzp|razorpay|payu|paytm|cashfree|ccavenue|billdesk|juspay|phonepe|pinelabs|easebuzz|bdsk)\b.*""",
    )
    private val noiseWords = Regex(
        """\b(ltd|limited|pvt|private|llp|inc|india|technologies|retail|online|services|payments?|mr|mrs|ms|dr|smt|shri|vpa|upi)\b""",
    )
    private val trailingCorporate = Regex("""(ltd|limited|pvt|private|india)+$""")

    fun key(raw: String): String {
        val lower = raw.lowercase().trim()
        val handle = lower.substringBefore('@')
        var s = handle.replace(gatewaySuffix, "")
        s = s.replace(Regex("""[^a-z0-9 ]"""), " ")
        s = s.replace(Regex("""\d+"""), " ")
        s = s.split(Regex("""\s+""")).joinToString(" ") { it.replace(trailingCorporate, "") }
        s = s.replace(noiseWords, " ")
        s = s.replace(Regex("""\s+"""), " ").trim()
        if (s.length < 2) {
            // Phone-number VPAs like 9876543210@ybl: keep the number itself.
            s = handle.replace(Regex("""[^a-z0-9]"""), "")
        }
        return s.ifEmpty { "unknown" }
    }

    /** "a sharma" -> "A Sharma" */
    fun displayName(raw: String): String {
        val k = key(raw)
        if (k == "unknown") return "Unknown"
        return k.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
