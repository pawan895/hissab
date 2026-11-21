package com.pawan.hisaab.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.ui.theme.FinTheme

/** Groups ordered by relevance to the direction of the money. */
fun groupedForDirection(
    groups: List<CategoryGroup>,
    categories: List<Category>,
    direction: Direction,
): List<Pair<CategoryGroup, List<Category>>> {
    // Debits: expenses, then savings, then income. Credits: income first.
    val rank = if (direction == Direction.DEBIT) {
        mapOf(GroupKind.EXPENSE to 0, GroupKind.SAVINGS to 1, GroupKind.INCOME to 2)
    } else {
        mapOf(GroupKind.INCOME to 0, GroupKind.EXPENSE to 1, GroupKind.SAVINGS to 2)
    }
    val active = categories.filter { !it.archived }
    return groups.sortedBy { it.sortOrder }.sortedBy { rank.getValue(it.kind) }
        .map { g -> g to active.filter { it.groupId == g.id }.sortedBy { it.sortOrder } }
        .filter { it.second.isNotEmpty() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryList(
    groups: List<Pair<CategoryGroup, List<Category>>>,
    selectedId: Long?,
    onPick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        groups.forEach { (group, cats) ->
            item(key = "g${group.id}") {
                Text(
                    group.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = FinTheme.colors.ink2,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                )
            }
            item(key = "c${group.id}") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    cats.forEach { c -> Pill("${c.emoji}  ${c.name}", c.id == selectedId, { onPick(c) }) }
                }
            }
        }
    }
}
