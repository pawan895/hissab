package com.pawan.hisaab.sms

import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.util.Money

data class ParsedTxn(
    val amountPaise: Long,
    val direction: Direction,
    val payee: String,
    val bank: String,
    val accountLast4: String = "",
    val refNo: String = "",
    val balancePaise: Long? = null,
    /** False when only the generic fallback matched; the UI asks the user to verify. */
    val confident: Boolean = true,
)

/**
 * Bank-specific rules first (exact formats seen in real SMS), then a cautious
 * generic fallback that only accepts messages that clearly describe a completed
 * debit or credit.
 */
object SmsParser {
    private const val AMT = """(?:Rs\.?|INR|₹)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""

    private class Rule(
        val bank: String,
        val regex: Regex,
        val build: (MatchResult) -> ParsedTxn?,
    )

    private fun r(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private fun dir(word: String): Direction =
        if (word.lowercase().startsWith("cred")) Direction.CREDIT else Direction.DEBIT

    private val rules = listOf(
        // HDFC credit card used on UPI:
        // "Txn Rs.204.05 On HDFC Bank Card 1111 At zomatoltd32.rzp@hdfcbank by UPI 400000000001 On 30-09"
        Rule("HDFC", r("""Txn $AMT On HDFC Bank Card x*(\d{4}) At (.+?) by UPI (\d+)""")) { m ->
            ParsedTxn(
                amountPaise = Money.parsePaise(m.groupValues[1]) ?: return@Rule null,
                direction = Direction.DEBIT,
                payee = m.groupValues[3].trim(),
                bank = "HDFC",
                accountLast4 = m.groupValues[2],
                refNo = m.groupValues[4],
            )
        },
        // HDFC savings account UPI debit: "Sent Rs.500.00 From HDFC Bank A/C *1234 To SOME NAME On 12/10/24 Ref 123456789012"
        Rule("HDFC", r("""Sent $AMT From HDFC Bank A/?C [*x]*(\d{4}) To (.+?) On \S+ Ref (\d+)""")) { m ->
            ParsedTxn(
                amountPaise = Money.parsePaise(m.groupValues[1]) ?: return@Rule null,
                direction = Direction.DEBIT,
                payee = m.groupValues[3].trim(),
                bank = "HDFC",
                accountLast4 = m.groupValues[2],
                refNo = m.groupValues[4],
            )
        },
        // HDFC credit: "Rs.500.00 credited to HDFC Bank A/c XX1234 on 12-10-24 from VPA abc@okicici (UPI 123456789012)"
        Rule("HDFC", r("""$AMT credited to HDFC Bank A/?c [*x]*(\d{4}) .*?from (?:VPA )?(\S+).*?\(UPI (?:Ref No\.? )?(\d+)\)""")) { m ->
            ParsedTxn(
                amountPaise = Money.parsePaise(m.groupValues[1]) ?: return@Rule null,
                direction = Direction.CREDIT,
                payee = m.groupValues[3].trim(),
                bank = "HDFC",
                accountLast4 = m.groupValues[2],
                refNo = m.groupValues[4],
            )
        },
        // KVB: "Your a/c XXXXXXXXXXXX2222 is debited Rs. 163.86 on 01-Nov-2026 to ZOMATO info :P2A/400000000002. Avl Bal INR 879.67"
        Rule("KVB", r("""a/c [x*]*(\d{4}) is (debited|credited) (?:with )?$AMT on \S+ (?:to|from|by) (.+?) info\s*:\s*([A-Z0-9/]+)""")) { m ->
            ParsedTxn(
                amountPaise = Money.parsePaise(m.groupValues[3]) ?: return@Rule null,
                direction = dir(m.groupValues[2]),
                payee = m.groupValues[4].trim(),
                bank = "KVB",
                accountLast4 = m.groupValues[1],
                refNo = Regex("""\d{6,}""").findAll(m.groupValues[5]).lastOrNull()?.value ?: "",
            )
        },
        // Federal: "Debited Rs 20000.00 from a/c X3333 on 14Oct26 18:03 via UPI to Mrs A Sharma. Ref 400000000003.Bal Rs 4214.54"
        Rule("Federal", r("""(Debited|Credited) $AMT (?:from|to|in) (?:your )?a/c [x*]*(\d{4}) on .+? via UPI (?:to|from) (.+?)\.\s?Ref:? ?(\d+)""")) { m ->
            ParsedTxn(
                amountPaise = Money.parsePaise(m.groupValues[2]) ?: return@Rule null,
                direction = dir(m.groupValues[1]),
                payee = m.groupValues[4].trim(),
                bank = "Federal",
                accountLast4 = m.groupValues[3],
                refNo = m.groupValues[5],
            )
        },
    )

    // ---- generic fallback ----

    private val rejectWords = r(
        """\b(otp|one[- ]time password|verification code|has requested|requested money|collect request|will be debited|""" +
            """is due|due on|due date|minimum amount|bill (?:is )?generated|statement|pre-?approved|offer|cashback up ?to|""" +
            """win|congratulations|apply now|emi of|failed|declined|reversed|unsuccessful)\b""",
    )
    private val debitWords = r("""\b(debited|spent|paid|sent|withdrawn|purchase|txn of|transaction of|payment of)\b""")
    private val creditWords = r("""\b(credited|received|deposited|refunded|refund of)\b""")
    private val accountHint = r("""\b(a/?c|acct|account|card|upi|vpa|imps|neft|rtgs)\b""")
    private val amount = r(AMT)
    private val balance = r("""\b(?:avl\.?\s*|available\s*)?(?:bal|balance)\b\.?\s*(?:is)?\s*:?\s*$AMT""")
    private val account = r("""(?:a/?c|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?)?\s*[x*]*(\d{3,4})\b""")
    private val ref = r("""(?:ref(?:erence)?\.?\s*(?:no\.?|number|id)?|upi(?:\s*ref)?|utr|rrn|txn\s*id)\s*[:#.-]?\s*(\d{6,})""")
    private val vpa = Regex("""\b([a-zA-Z0-9._-]{2,}@[a-zA-Z]{2,})\b""")
    private val payeeAfter = r("""\b(?:to|at|towards|from|by)\s+(?!a/?c\b|your\b|you\b)([A-Za-z0-9&'._ -]{2,40}?)(?=\s+(?:on|via|ref|upi|info|avl|thru|using)\b|[.(,]|$)""")

    private val bankNames = linkedMapOf(
        "hdfc" to "HDFC", "kvb" to "KVB", "karur vysya" to "KVB", "federal" to "Federal", "fedbnk" to "Federal",
        "sbi" to "SBI", "icici" to "ICICI", "axis" to "Axis", "kotak" to "Kotak", "yes bank" to "Yes Bank",
        "indusind" to "IndusInd", "idfc" to "IDFC First", "canara" to "Canara", "pnb" to "PNB",
        "bank of baroda" to "BoB", "union bank" to "Union Bank", "indian bank" to "Indian Bank", "iob" to "IOB",
        "paytm" to "Paytm", "airtel" to "Airtel Payments",
    )

    fun normalize(body: String): String = body.replace(Regex("""\s+"""), " ").trim()

    fun parse(sender: String, rawBody: String): ParsedTxn? {
        val body = normalize(rawBody)
        if (body.isEmpty()) return null

        for (rule in rules) {
            val m = rule.regex.find(body) ?: continue
            val parsed = rule.build(m) ?: continue
            if (parsed.amountPaise <= 0) continue
            return parsed.copy(balancePaise = findBalance(body))
        }
        return parseGeneric(sender, body)
    }

    private fun parseGeneric(sender: String, body: String): ParsedTxn? {
        if (rejectWords.containsMatchIn(body)) return null
        if (!accountHint.containsMatchIn(body)) return null
        val d = debitWords.find(body)
        val c = creditWords.find(body)
        val direction = when {
            d == null && c == null -> return null
            d == null -> Direction.CREDIT
            c == null -> Direction.DEBIT
            d.range.first <= c.range.first -> Direction.DEBIT
            else -> Direction.CREDIT
        }
        val amt = amount.findAll(body).firstOrNull { m ->
            val before = body.substring(maxOf(0, m.range.first - 25), m.range.first).lowercase()
            "bal" !in before && "limit" !in before
        } ?: return null
        val paise = Money.parsePaise(amt.groupValues[1]) ?: return null
        if (paise <= 0) return null

        val payee = vpa.find(body)?.groupValues?.get(1)
            ?: payeeAfter.findAll(body).map { it.groupValues[1].trim() }
                .firstOrNull { p -> bankNames.keys.none { p.lowercase().contains(it) } }
            ?: "Unknown"

        return ParsedTxn(
            amountPaise = paise,
            direction = direction,
            payee = payee,
            bank = detectBank(sender, body),
            accountLast4 = account.find(body)?.groupValues?.get(1) ?: "",
            refNo = ref.find(body)?.groupValues?.get(1) ?: "",
            balancePaise = findBalance(body),
            confident = false,
        )
    }

    private fun findBalance(body: String): Long? =
        balance.find(body)?.groupValues?.get(1)?.let { Money.parsePaise(it) }

    fun detectBank(sender: String, body: String): String {
        val hay = (sender + " " + body).lowercase()
        return bankNames.entries.firstOrNull { hay.contains(it.key) }?.value ?: sender.substringAfter('-').take(8)
    }
}
