package com.pawan.hisaab.data

import com.pawan.hisaab.data.seed.DefaultCategories
import com.pawan.hisaab.data.seed.KnownMerchants
import com.pawan.hisaab.sms.MerchantNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownMerchantsTest {
    private fun cat(raw: String) = KnownMerchants.categoryFor(MerchantNormalizer.key(raw), raw)

    @Test fun recognisesCommonMerchants() {
        assertEquals("Online Food orders", cat("zomatoltd32.rzp@hdfcbank"))
        assertEquals("Online Food orders", cat("ZOMATO"))
        assertEquals("Online Food orders", cat("swiggy.payu@axisbank"))
        assertEquals("Groceries", cat("swiggyinstamart@icici"))
        assertEquals("Groceries", cat("zeptomarketplace@ybl"))
        assertEquals("Groceries", cat("BLINKIT"))
        assertEquals("Groceries", cat("jiomart@sbi"))
        assertEquals("Phone", cat("JIO PREPAID RECHARGE"))
        assertEquals("Cab / Auto", cat("olacabs@ybl"))
        assertEquals("Cab / Auto", cat("Uber India"))
        assertEquals("Fuel", cat("HPCL PETROL PUMP"))
        assertEquals("Gas / Oil", cat("Bharat Gas"))
        assertEquals("OTT", cat("netflix.payu@hdfcbank"))
        assertEquals("Medicine / Prescriptions", cat("Tata 1mg"))
        assertEquals("Dining Out", cat("BARBEQUE NATION"))
    }

    @Test fun leavesPeopleAndLookalikesAlone() {
        assertNull(cat("Mrs A Sharma"))
        assertNull(cat("cocacola@okaxis"))
        assertNull(cat("9876543210@ybl"))
        assertNull(cat("Ravi Kumar"))
    }

    @Test fun everyCategoryExistsInTheSeed() {
        val seeded = DefaultCategories.groups.flatMap { g -> g.categories.map { it.first } }.toSet()
        val raws = listOf("zepto", "zomato", "starbucks", "liquor", "pvr", "netflix", "airtel", "hathway", "bescom", "indane",
            "hpcl", "uber", "irctc", "pharmeasy", "hospital", "cultfit", "amazon")
        raws.forEach { r -> assertTrue(r, KnownMerchants.categoryFor(r) in seeded) }
    }
}
