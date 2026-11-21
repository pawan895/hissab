package com.pawan.hisaab.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.Bill
import com.pawan.hisaab.data.Sharing
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.formatDate
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.ui.components.Hairline
import com.pawan.hisaab.ui.components.Meter
import com.pawan.hisaab.ui.components.MoneyText
import com.pawan.hisaab.ui.components.SectionLabel
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money

/** Bills you paid for friends: who still owes you, and what came back. */
@Composable
fun SharedBillsScreen(
    bills: List<Bill>,
    onOpen: (Txn) -> Unit,
    onSettle: (Bill) -> Unit,
    contentPadding: PaddingValues,
) {
    val c = FinTheme.colors
    val open = bills.filter { it.outstanding > 0 }
    val done = bills.filter { it.outstanding == 0L }.take(20)

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Friends owe you", style = MaterialTheme.typography.labelMedium, color = c.ink2)
                MoneyText(open.sumOf { it.outstanding }, style = MaterialTheme.typography.displayLarge)
                Text(
                    "Split a bill from its payment or the popup. When a friend sends their share, " +
                        "pick the bill and it comes off what the bill cost you instead of counting as income.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (open.isNotEmpty()) {
            item { SectionLabel("Waiting") }
            items(open, key = { "o${it.txn.id}" }) { b -> BillRow(b, onOpen, onSettle) }
        }
        if (done.isNotEmpty()) {
            item { SectionLabel("Paid back") }
            items(done, key = { "d${it.txn.id}" }) { b -> BillRow(b, onOpen, null) }
        }
        if (bills.isEmpty()) {
            item {
                Text(
                    "No shared bills yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink3,
                    modifier = Modifier.padding(top = 28.dp),
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun BillRow(b: Bill, onOpen: (Txn) -> Unit, onSettle: ((Bill) -> Unit)?) {
    val c = FinTheme.colors
    val t = b.txn
    val friends = t.amountPaise - b.cost
    val people = Sharing.peopleFor(t)
    Column(Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(MerchantNormalizer.displayName(t.merchantRaw), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        t.timestamp.formatDate("d MMM"),
                        "paid ${Money.format(t.amountPaise)}",
                        when {
                            people != null && people > 1 -> "split $people ways"
                            t.sharePaise != null -> "your share ${Money.format(t.sharePaise)}"
                            else -> null
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (b.outstanding > 0) {
                    MoneyText(b.outstanding)
                    Text("to come back", style = MaterialTheme.typography.labelSmall, color = c.ink3)
                } else {
                    MoneyText(b.cost)
                    Text("cost you", style = MaterialTheme.typography.labelSmall, color = c.ink3)
                }
            }
        }
        if (friends > 0) {
            Spacer(Modifier.height(10.dp))
            Meter(b.received.toFloat() / (b.received + b.outstanding).coerceAtLeast(1), c.credit)
        }
        b.paybacks.forEach { p ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "↩  ${MerchantNormalizer.displayName(p.merchantRaw)} · ${p.timestamp.formatDate("d MMM")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink2,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("+" + Money.format(p.amountPaise), style = MaterialTheme.typography.bodySmall.merge(Numbers), color = c.credit)
            }
        }
        if (onSettle != null) {
            Text(
                "Settle · stop waiting for ${Money.format(b.outstanding)}",
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).clickable { onSettle(b) }.padding(vertical = 4.dp),
            )
        }
    }
    Hairline()
}
