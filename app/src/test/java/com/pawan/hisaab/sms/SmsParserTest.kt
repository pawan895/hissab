package com.pawan.hisaab.sms

import com.pawan.hisaab.data.db.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private val hdfcCard = """Txn Rs.204.05
On HDFC Bank Card 1111
At zomatoltd32.rzp@hdfcbank
by UPI 400000000001
On 30-09
Not You?
Call 18002586161/SMS BLOCK CC 1111 to 7308080808"""

    private val kvb = "Your a/c XXXXXXXXXXXX2222 is debited Rs. 163.86 on 01-Nov-2026 to ZOMATO info :P2A/400000000002. " +
        "Avl Bal INR 879.67 Not You? call 18005721916-KVB"

    private val federal = "Debited Rs 20000.00 from a/c X3333 on 14Oct26 18:03 via UPI to Mrs A Sharma. Ref 400000000003." +
        "Bal Rs 4214.54. Not you?Call 18004251199 -Federal Bank"

    @Test fun hdfcCreditCardUpi() {
        val p = SmsParser.parse("VM-HDFCBK", hdfcCard)
        assertNotNull(p); p!!
        assertEquals(20405L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("HDFC", p.bank)
        assertEquals("1111", p.accountLast4)
        assertEquals("400000000001", p.refNo)
        assertEquals("zomato", MerchantNormalizer.key(p.payee))
        assertTrue(p.confident)
    }

    @Test fun kvbDebit() {
        val p = SmsParser.parse("JD-KVBANK", kvb)!!
        assertEquals(16386L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("KVB", p.bank)
        assertEquals("2222", p.accountLast4)
        assertEquals("400000000002", p.refNo)
        assertEquals(87967L, p.balancePaise)
        assertEquals("zomato", MerchantNormalizer.key(p.payee))
    }

    @Test fun federalDebit() {
        val p = SmsParser.parse("AX-FEDBNK", federal)!!
        assertEquals(2000000L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("Federal", p.bank)
        assertEquals("3333", p.accountLast4)
        assertEquals("400000000003", p.refNo)
        assertEquals(421454L, p.balancePaise)
        assertEquals("Mrs A Sharma", p.payee)
        assertEquals("a sharma", MerchantNormalizer.key(p.payee))
    }

    @Test fun kvbCredit() {
        val p = SmsParser.parse(
            "JD-KVBANK",
            "Your a/c XXXXXXXXXXXX2222 is credited Rs. 84,700.00 on 31-Oct-2026 by ACME PVT LTD info :NEFT/AXN123456789. Avl Bal INR 85,579.67",
        )!!
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(8470000L, p.amountPaise)
        assertEquals("acme", MerchantNormalizer.key(p.payee))
    }

    @Test fun federalCredit() {
        val p = SmsParser.parse(
            "AX-FEDBNK",
            "Credited Rs 500.00 to a/c X3333 on 15Oct26 09:10 via UPI from Ravi Kumar. Ref 400000000004.Bal Rs 4714.54. -Federal Bank",
        )!!
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(50000L, p.amountPaise)
        assertEquals("Ravi Kumar", p.payee)
    }

    @Test fun genericOtherBankDebit() {
        val p = SmsParser.parse(
            "AD-SBIUPI",
            "Dear UPI user A/C X1234 debited by 250.00 on date 20Oct26 trf to SWIGGY Refno 429876543210. If not u? call 1800111109. -SBI",
        )
        // No currency symbol before amount -> not confidently parseable; must not crash.
        if (p != null) assertFalse(p.confident)

        val q = SmsParser.parse("VK-ICICIB", "ICICI Bank Acct XX123 debited for Rs 1,250.50 on 21-Oct-26; swiggy@icici credited. UPI:429876543211.")!!
        assertEquals(125050L, q.amountPaise)
        assertEquals(Direction.DEBIT, q.direction)
        assertEquals("ICICI", q.bank)
        assertEquals("swiggy", MerchantNormalizer.key(q.payee))
        assertEquals("429876543211", q.refNo)
        assertFalse(q.confident)
    }

    @Test fun rejectsOtpPromoAndRequests() {
        assertNull(SmsParser.parse("VM-HDFCBK", "123456 is your OTP for txn of Rs 2,000.00 at AMAZON on HDFC Bank Card 1111. Do not share."))
        assertNull(SmsParser.parse("VM-HDFCBK", "Get a pre-approved loan of Rs 5,00,000 credited to your account instantly! Apply now."))
        assertNull(SmsParser.parse("VM-GPAY", "Ravi has requested money Rs 300 from you on Google Pay UPI. Pay now."))
        assertNull(SmsParser.parse("VM-HDFCBK", "Your HDFC Bank Credit Card bill of Rs 12,000 is due on 05-Nov. Minimum amount due Rs 600."))
        assertNull(SmsParser.parse("+919876543210", "Hey, are we meeting today?"))
    }

    @Test fun normalizerHandlesVpas() {
        assertEquals("swiggy", MerchantNormalizer.key("swiggy.rzp@axisbank"))
        assertEquals("9876543210", MerchantNormalizer.key("9876543210@ybl"))
        assertEquals("amazon", MerchantNormalizer.key("amazonindia@apl"))
        assertEquals("Zomato", MerchantNormalizer.displayName("ZOMATO"))
    }
}
