package com.pawan.hisaab.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.ui.MainViewModel
import com.pawan.hisaab.ui.components.ListRow
import com.pawan.hisaab.ui.components.Pill
import com.pawan.hisaab.ui.components.PlainField
import com.pawan.hisaab.ui.components.SecondaryButton
import com.pawan.hisaab.ui.components.SectionLabel
import com.pawan.hisaab.ui.theme.FinTheme

private sealed interface Editing {
    data class NewCategory(val group: CategoryGroup) : Editing
    data class EditCategory(val category: Category) : Editing
    data object NewGroup : Editing
    data class EditGroup(val group: CategoryGroup) : Editing
}

@Composable
fun CategoriesScreen(vm: MainViewModel, groups: List<CategoryGroup>, categories: List<Category>, contentPadding: PaddingValues) {
    val c = FinTheme.colors
    var editing by remember { mutableStateOf<Editing?>(null) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        groups.forEach { g ->
            item(key = "g${g.id}") { SectionLabel(g.name, action = "Rename", onAction = { editing = Editing.EditGroup(g) }) }
            items(categories.filter { it.groupId == g.id }, key = { "c${it.id}" }) { cat ->
                ListRow(
                    emoji = cat.emoji,
                    title = cat.name,
                    subtitle = if (cat.archived) "Hidden" else null,
                    onClick = { editing = Editing.EditCategory(cat) },
                    modifier = Modifier.alpha(if (cat.archived) 0.5f else 1f),
                )
            }
            item(key = "a${g.id}") {
                Text(
                    "+ Add to ${g.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.clickable { editing = Editing.NewCategory(g) }.padding(vertical = 10.dp),
                )
            }
        }
        item { SecondaryButton("New group", { editing = Editing.NewGroup }, Modifier.padding(top = 28.dp, bottom = 32.dp)) }
    }

    when (val e = editing) {
        null -> {}
        is Editing.NewCategory -> CategoryDialog("New in ${e.group.name}", "", "", null, { editing = null }) { name, emoji, _ ->
            vm.addCategory(e.group.id, name, emoji); editing = null
        }
        is Editing.EditCategory -> CategoryDialog("Edit category", e.category.name, e.category.emoji, e.category.archived, { editing = null }) { name, emoji, archived ->
            vm.updateCategory(e.category.copy(name = name, emoji = emoji.ifBlank { "•" }, archived = archived ?: false)); editing = null
        }
        Editing.NewGroup -> GroupDialog("New group", "", GroupKind.EXPENSE, { editing = null }) { name, kind ->
            vm.addGroup(name, kind); editing = null
        }
        is Editing.EditGroup -> GroupDialog("Rename group", e.group.name, null, { editing = null }) { name, _ ->
            vm.updateGroup(e.group.copy(name = name)); editing = null
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    archived: Boolean?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    var hidden by remember { mutableStateOf(archived ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(title) },
        text = {
            Column {
                PlainField(name, { name = it }, "Name")
                PlainField(emoji, { emoji = it.take(4) }, "Emoji")
                if (archived != null) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Hide from popup", modifier = Modifier.weight(1f))
                        Switch(checked = hidden, onCheckedChange = { hidden = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), emoji.trim(), if (archived == null) null else hidden) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupDialog(
    title: String,
    initialName: String,
    initialKind: GroupKind?,
    onDismiss: () -> Unit,
    onSave: (String, GroupKind) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind ?: GroupKind.EXPENSE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(title) },
        text = {
            Column {
                PlainField(name, { name = it }, "Name")
                if (initialKind != null) {
                    Row(Modifier.padding(top = 14.dp)) {
                        GroupKind.entries.forEach { k ->
                            Pill(k.name.lowercase().replaceFirstChar { it.uppercase() }, kind == k, { kind = k }, Modifier.padding(end = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), kind) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
