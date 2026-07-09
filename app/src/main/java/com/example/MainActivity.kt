package com.example

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.BackgroundColor
import com.example.ui.theme.SurfaceCardColor
import com.example.ui.theme.SecondarySurfaceColor
import com.example.ui.theme.InputFieldColor
import com.example.ui.theme.DividerColor
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextDisabled
import com.example.ui.theme.MyApplicationTheme
import java.util.Calendar
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                val caseViewModel: CaseViewModel = viewModel()
                MainAppScreen(caseViewModel)
            }
        }
    }
}

sealed interface Filter {
    object All : Filter
    data class Status(val status: String) : Filter
    data class MonthYear(val year: String, val month: String) : Filter {
        override fun toString(): String = "$month $year"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: CaseViewModel) {
    val context = LocalContext.current
    val directoryUriState = viewModel.directoryUri.collectAsState()
    val casesState = viewModel.cases.collectAsState()
    val isLoadingState = viewModel.isLoading.collectAsState()

    var currentFilter by remember { mutableStateOf<Filter>(Filter.All) }
    var searchText by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var caseToEdit by remember { mutableStateOf<CaseRecord?>(null) }
    
    // State of selected cases for multi-delete
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedCases by remember { mutableStateOf(setOf<CaseRecord>()) }
    
    // Dialog state
    var showCaseDetailDialog by remember { mutableStateOf<CaseRecord?>(null) }
    var showPurgeConfirmation by remember { mutableStateOf(false) }
    var showMultiDeleteConfirmation by remember { mutableStateOf(false) }
    var purgeConfirmationInput by remember { mutableStateOf("") }
    var multiDeleteConfirmationInput by remember { mutableStateOf("") }

    // Dropdown state for tree year navigation
    var expandedYears by remember { mutableStateOf(setOf<String>()) }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Intent launcher for SAF tree directory selection
    val dirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    viewModel.setDirectoryUri(uri)
                    Toast.makeText(context, "Storage folder configured successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    viewModel.setDirectoryUri(uri)
                    Toast.makeText(context, "Directory configured.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // Compute dynamic folders grouped by Year and Month from current in-memory data
    val folderStructure = remember(casesState.value) {
        val map = mutableMapOf<String, MutableSet<String>>()
        casesState.value.forEach { record ->
            val year = CaseRecordMapper.getYearFromDate(record.intakeDate)
            val month = CaseRecordMapper.getMonthFromDate(record.intakeDate)
            map.getOrPut(year) { mutableSetOf() }.add(month)
        }
        map.mapValues { entry ->
            entry.value.sortedWith(compareBy { getMonthOrder(it) })
        }.toSortedMap(compareByDescending { it })
    }

    if (directoryUriState.value == null) {
        // Safe check / Onboarding selection screen
        OnboardingScreen(onSelectDirectory = {
            dirLauncher.launch(null)
        })
    } else {
        // App structured with standard Sidebar Drawer navigation
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(320.dp),
                    drawerContainerColor = BackgroundColor
                ) {
                    SideDrawerContent(
                        cases = casesState.value,
                        folderStructure = folderStructure,
                        currentFilter = currentFilter,
                        directoryUri = directoryUriState.value,
                        onFilterSelected = { filter ->
                            currentFilter = filter
                            searchText = "" // Reset search when clicking filters
                            isSelectionMode = false
                            selectedCases = emptySet()
                            scope.launch { drawerState.close() }
                        },
                        onClearDirectory = {
                            viewModel.clearDirectoryUri()
                        },
                        onRefresh = {
                            viewModel.loadRecords()
                        },
                        expandedYears = expandedYears,
                        onToggleYear = { year ->
                            expandedYears = if (expandedYears.contains(year)) {
                                expandedYears - year
                            } else {
                                expandedYears + year
                            }
                        },
                        onTriggerPurge = {
                            showPurgeConfirmation = true
                            purgeConfirmationInput = ""
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Cachar DLSA Registry",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (val filter = currentFilter) {
                                        is Filter.All -> "All Records • ${casesState.value.size}"
                                        is Filter.Status -> "Filtered • ${filter.status}"
                                        is Filter.MonthYear -> "Folder • ${filter.month} ${filter.year}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            if (showCaseDetailDialog != null) {
                                IconButton(onClick = { showCaseDetailDialog = null }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to List",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open Drawer Menu",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        actions = {
                            // Selection Mode toggle
                            if (casesState.value.isNotEmpty()) {
                                IconButton(onClick = {
                                    isSelectionMode = !isSelectionMode
                                    if (!isSelectionMode) {
                                        selectedCases = emptySet()
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isSelectionMode) Icons.Default.Close else Icons.Default.EditCalendar,
                                        contentDescription = "Toggle Selection Mode",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Export Button (TSV to clipboard & share)
                            if (casesState.value.isNotEmpty()) {
                                IconButton(onClick = {
                                    val tsvText = exportToTsv(casesState.value)
                                    // Copy to clipboard
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Cachar DLSA Case TSV Export", tsvText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "TSV copied to clipboard!", Toast.LENGTH_SHORT).show()

                                    // Share intent
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Cachar DLSA Case Registry Export")
                                        putExtra(Intent.EXTRA_TEXT, tsvText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Case Records (TSV)"))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Export and Share Database",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Delete selected button
                            if (isSelectionMode && selectedCases.isNotEmpty()) {
                                IconButton(onClick = {
                                    showMultiDeleteConfirmation = true
                                    multiDeleteConfirmationInput = ""
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Selected Records",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            caseToEdit = null
                            showAddEditDialog = true
                        },
                        containerColor = PrimaryBlue,
                        contentColor = TextPrimary,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.testTag("add_case_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Register New Case",
                            tint = TextPrimary
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val filteredCases = remember(casesState.value, currentFilter, searchText) {
                        val query = searchText.lowercase().trim()
                        casesState.value.filter { case ->
                            val matchesFilter = when (val filter = currentFilter) {
                                is Filter.All -> true
                                is Filter.Status -> case.status.equals(filter.status, ignoreCase = true)
                                is Filter.MonthYear -> {
                                    val yr = CaseRecordMapper.getYearFromDate(case.intakeDate)
                                    val mon = CaseRecordMapper.getMonthFromDate(case.intakeDate)
                                    yr == filter.year && mon.equals(filter.month, ignoreCase = true)
                                }
                            }

                            val matchesSearch = if (query.isEmpty()) {
                                true
                            } else {
                                case.searchIndex.contains(query)
                            }

                            matchesFilter && matchesSearch
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search text field
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = InputFieldColor,
                            border = BorderStroke(1.dp, DividerColor),
                            shadowElevation = 0.dp
                        ) {
                            TextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholder = { 
                                    Text(
                                        text = "Search by No., Petitioner, Opponent...",
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.Search, 
                                        contentDescription = "Search Icon",
                                        tint = TextMuted
                                    ) 
                                },
                                trailingIcon = {
                                    if (searchText.isNotEmpty()) {
                                        IconButton(onClick = { searchText = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear, 
                                                contentDescription = "Clear Search",
                                                tint = TextSecondary
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            scope.launch {
                                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Filters",
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_field_case")
                                    .height(54.dp)
                            )
                        }

                        if (isSelectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedCases.size} records selected for deletion",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    onClick = {
                                        showMultiDeleteConfirmation = true
                                        multiDeleteConfirmationInput = ""
                                    },
                                    enabled = selectedCases.isNotEmpty(),
                                    modifier = Modifier.testTag("multi_delete_trigger_btn")
                                ) {
                                    Text("Delete Selected", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (showCaseDetailDialog != null) {
                            val detailCase = showCaseDetailDialog!!
                            BackHandler(enabled = true) {
                                showCaseDetailDialog = null
                            }
                            CaseDetailContent(
                                case = detailCase,
                                onDismiss = { showCaseDetailDialog = null },
                                onEdit = {
                                    caseToEdit = detailCase
                                    showCaseDetailDialog = null
                                    showAddEditDialog = true
                                },
                                onDelete = {
                                    viewModel.deleteCaseRecord(detailCase) { deleted ->
                                        showCaseDetailDialog = null
                                        if (deleted) {
                                            Toast.makeText(context, "Case deleted successfully", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to delete case file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onStatusChange = { newStatus ->
                                    val updatedCase = detailCase.copy(status = newStatus)
                                    viewModel.saveCaseRecord(updatedCase) { success, msg ->
                                        if (success) {
                                            showCaseDetailDialog = updatedCase
                                            Toast.makeText(context, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to update status: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onNextDateChange = { newNextDate ->
                                    val updatedCase = detailCase.copy(nextDate = newNextDate)
                                    viewModel.saveCaseRecord(updatedCase) { success, msg ->
                                        if (success) {
                                            showCaseDetailDialog = updatedCase
                                            Toast.makeText(context, "Next date updated to ${formatToDisplayImage(newNextDate)}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Failed to update next date: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else if (isLoadingState.value) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (filteredCases.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Empty Directory",
                                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No cases match the query or filter.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Press (+) below to register a new file or select a different folder in the sidebar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredCases, key = { it.caseNumber }) { case ->
                                    CaseRecordBentoCard(
                                        case = case,
                                        isSelected = selectedCases.contains(case),
                                        isSelectionMode = isSelectionMode,
                                        onToggleSelected = {
                                            selectedCases = if (selectedCases.contains(case)) {
                                                selectedCases - case
                                            } else {
                                                selectedCases + case
                                            }
                                        },
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedCases = if (selectedCases.contains(case)) {
                                                    selectedCases - case
                                                } else {
                                                    selectedCases + case
                                                }
                                            } else {
                                                showCaseDetailDialog = case
                                            }
                                        },
                                        onEditClick = {
                                            caseToEdit = case
                                            showAddEditDialog = true
                                        },
                                        onDeleteClick = {
                                            viewModel.deleteCaseRecord(case) { deleted ->
                                                if (deleted) {
                                                    Toast.makeText(context, "Case deleted successfully", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to delete case file", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onStatusChange = { newStatus ->
                                            val updatedCase = case.copy(status = newStatus)
                                            viewModel.saveCaseRecord(updatedCase) { success, msg ->
                                                if (success) {
                                                    Toast.makeText(context, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to update status: $msg", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onNextDateChange = { newNextDate ->
                                            val updatedCase = case.copy(nextDate = newNextDate)
                                            viewModel.saveCaseRecord(updatedCase) { success, msg ->
                                                if (success) {
                                                    Toast.makeText(context, "Next date updated to ${formatToDisplayImage(newNextDate)}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to update next date: $msg", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Form Dialog for Registering and Editing Cases
    if (showAddEditDialog) {
        val localCaseToEdit = caseToEdit
        AddEditCaseDialog(
            caseToEdit = localCaseToEdit,
            suggestedSerialNum = viewModel.getNextSerialNumber(),
            onDismiss = { showAddEditDialog = false },
            onSave = { updatedCase ->
                if (localCaseToEdit != null && (localCaseToEdit.caseNumber != updatedCase.caseNumber || localCaseToEdit.year != updatedCase.year)) {
                    viewModel.deleteCaseRecord(localCaseToEdit) { deleted ->
                        viewModel.saveCaseRecord(updatedCase) { success, message ->
                            if (success) {
                                showAddEditDialog = false
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    viewModel.saveCaseRecord(updatedCase) { success, message ->
                        if (success) {
                            showAddEditDialog = false
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }



    // Safety Gate Confirmation Dialog for Purging Database
    if (showPurgeConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmation = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning icon", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Danger Zone: PURGE ALL")
                }
            },
            text = {
                Column {
                    Text(
                        text = "This action will permanently delete ALL case record files in the storage directory. If you confirm, there is absolutely NO way to recover them.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "To authorize this action, type below: DELETE ALL",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = purgeConfirmationInput,
                        onValueChange = { purgeConfirmationInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purge_input_gate"),
                        placeholder = { Text("DELETE ALL") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (purgeConfirmationInput == "DELETE ALL") {
                            viewModel.purgeAllRecords { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                showPurgeConfirmation = false
                            }
                        } else {
                            Toast.makeText(context, "Confirmation text incorrect", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                    modifier = Modifier.testTag("confirm_purge_btn")
                ) {
                    Text("Delete All Files")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Safety Gate Confirmation Dialog for Multi-Delete Selected
    if (showMultiDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirmation = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Bulk Deletion")
                }
            },
            text = {
                Column {
                    Text(
                        text = "You are about to delete ${selectedCases.size} selected case records from your device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Type DELETE ALL to authorize this bulk action:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = multiDeleteConfirmationInput,
                        onValueChange = { multiDeleteConfirmationInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_delete_gate_input"),
                        placeholder = { Text("DELETE ALL") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (multiDeleteConfirmationInput == "DELETE ALL") {
                            var deleteSuccessCount = 0
                            val bulkList = selectedCases.toList()
                            
                            // Sequential deleting
                            for (case in bulkList) {
                                viewModel.deleteCaseRecord(case) { success ->
                                    if (success) deleteSuccessCount++
                                }
                            }
                            
                            Toast.makeText(context, "Deleted $deleteSuccessCount files", Toast.LENGTH_SHORT).show()
                            selectedCases = emptySet()
                            isSelectionMode = false
                            showMultiDeleteConfirmation = false
                        } else {
                            Toast.makeText(context, "Confirmation incorrect", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                    modifier = Modifier.testTag("confirm_bulk_btn")
                ) {
                    Text("Bulk Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Custom Drawer Item to support exquisite high-fidelity design
@Composable
fun CustomDrawerItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    badge: String? = null,
    showArrow: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PrimaryBlue.copy(alpha = 0.08f) else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .height(48.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (selected) {
                // High contrast left indicator bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(24.dp)
                        .background(PrimaryBlue, shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .align(Alignment.CenterStart)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (selected) 16.dp else 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(modifier = Modifier.width(12.dp))

                // Label Text
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = if (selected) PrimaryBlue else TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                // Optional Badge Count
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(SecondarySurfaceColor, shape = RoundedCornerShape(8.dp))
                            .border(1.dp, DividerColor, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = TextSecondary
                        )
                    }
                }

                // Optional Arrow chevron
                if (showArrow) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Side Navigation Drawer Content Redesigned
@Composable
fun SideDrawerContent(
    cases: List<CaseRecord>,
    folderStructure: Map<String, List<String>>,
    currentFilter: Filter,
    directoryUri: String?,
    onFilterSelected: (Filter) -> Unit,
    onClearDirectory: () -> Unit,
    onRefresh: () -> Unit,
    expandedYears: Set<String>,
    onToggleYear: (String) -> Unit,
    onTriggerPurge: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        // 1. DLSA / Mediation Centre Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 20.dp, start = 24.dp, end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(SecondarySurfaceColor, shape = RoundedCornerShape(14.dp))
                    .border(1.dp, DividerColor, shape = RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Balance,
                    contentDescription = "Scales of Justice Logo",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "District Legal Services\nAuthority",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.5.sp
                ),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Mediation Centre, Cachar",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Case Records Registry",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp
                ),
                color = TextMuted
            )
        }

        // 2. Active Registry Folder Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(SurfaceCardColor, shape = RoundedCornerShape(16.dp))
                .border(1.dp, DividerColor, shape = RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SecondarySurfaceColor, shape = RoundedCornerShape(8.dp))
                            .border(1.dp, DividerColor, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Active Folder Icon",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Linked Registry Folder",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val friendlyPath = directoryUri?.substringAfterLast("%3A")?.replace("%2F", "/") ?: "None"
                        Text(
                            text = "Path: $friendlyPath",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(32.dp)
                        .background(SecondarySurfaceColor, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, DividerColor, shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Reload Files",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClearDirectory,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DividerColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = TextSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Detach Folder",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Scrollable Tree & Filters List
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            // All Cases Shortcut
            CustomDrawerItem(
                label = "All Cases Registry (${cases.size})",
                selected = currentFilter is Filter.All,
                onClick = { onFilterSelected(Filter.All) },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.List,
                        contentDescription = null,
                        tint = if (currentFilter is Filter.All) PrimaryBlue else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            HorizontalDivider(
                color = DividerColor,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Dynamic Directories Header
            Text(
                text = "CASE DIRECTORIES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = TextMuted,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )

            if (folderStructure.isEmpty()) {
                Text(
                    text = "No dynamic directories found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            } else {
                folderStructure.forEach { (year, months) ->
                    val isYearExpanded = expandedYears.contains(year)
                    Column {
                        // Year folder expandable row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleYear(year) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isYearExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isYearExpanded) PrimaryBlue else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Year $year Folder",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isYearExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isYearExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 24.dp)
                            ) {
                                months.forEach { monthName ->
                                    val isSelectedFolder = currentFilter is Filter.MonthYear && 
                                            currentFilter.year == year && 
                                            currentFilter.month == monthName
                                    
                                    CustomDrawerItem(
                                        label = monthName,
                                        selected = isSelectedFolder,
                                        onClick = { onFilterSelected(Filter.MonthYear(year, monthName)) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = if (isSelectedFolder) PrimaryBlue else TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = DividerColor,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Status shortcut Section Header
            Text(
                text = "STATUS SHORTCUTS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = TextMuted,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )

            listOf("Registered", "Settled", "Not Settled", "Mediation 1.0").forEach { statusName ->
                val isSelectedStatus = currentFilter is Filter.Status && currentFilter.status == statusName
                val statusColor = getStatusColor(statusName)
                val statusIcon = when (statusName) {
                    "Settled" -> Icons.Default.CheckCircle
                    "Not Settled" -> Icons.Default.Cancel
                    "Mediation 1.0" -> Icons.Default.Gavel
                    else -> Icons.Default.HourglassEmpty
                }
                val count = cases.count { it.status == statusName }

                CustomDrawerItem(
                    label = statusName,
                    selected = isSelectedStatus,
                    onClick = { onFilterSelected(Filter.Status(statusName)) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(statusColor.copy(alpha = 0.12f), shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    },
                    badge = count.toString(),
                    showArrow = true
                )
            }
        }

        // 4. Bottom Controls (Purge Workspace, Settings, Help)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed.copy(alpha = 0.10f),
                    contentColor = ErrorRed
                ),
                onClick = onTriggerPurge,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, ErrorRed.copy(alpha = 0.25f), shape = RoundedCornerShape(12.dp))
                    .testTag("purge_button_trigger")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Purge Workspace",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Settings & Help & Support row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            Toast.makeText(context, "Settings option coming soon!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(DividerColor)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            Toast.makeText(context, "Help & Support coming soon!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = "Help & Support",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Help & Support",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// Onboarding Welcome Landing Form
@Composable
fun OnboardingScreen(onSelectDirectory: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCardColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, DividerColor, RoundedCornerShape(20.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Balance,
                    contentDescription = "Scales of Justice logo",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CACHAR DISTRICT LEGAL SERVICES AUTHORITY",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = TextPrimary
                )
                Text(
                    text = "SILCHAR, ASSAM",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Welcome to the Cachar DLSA Case Records Registry",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please select or create an empty folder on your physical storage to initialize your offline registry database. Case files are persisted strictly locally inside this designated folder.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onSelectDirectory,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboard_dir_btn")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Repository Folder", color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// Case Card representation utilizing modern bento structures
@Composable
fun CaseRecordBentoCard(
    case: CaseRecord,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelected: () -> Unit,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isDetailView: Boolean = false,
    onStatusChange: ((String) -> Unit)? = null,
    onNextDateChange: ((String) -> Unit)? = null
) {
    var showStatusDropdown by remember { mutableStateOf(false) }
    var showVersionDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else DividerColor,
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCardColor
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // ROW 1: HEADERS & VALUES
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelected() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = Color.Gray
                        ),
                        modifier = Modifier.testTag("checkbox_${case.caseNumber}").padding(end = 4.dp)
                    )
                }

                // The info columns: CASE, ID / YEAR, PARTIES
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // COLUMN 1: CASE
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = "CASE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = case.category.uppercase(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(DividerColor.copy(alpha = 0.4f))
                    )

                    // COLUMN 2: ID / YEAR
                    Column(modifier = Modifier.weight(2.5f)) {
                        Text(
                            text = "ID / YEAR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${case.caseNumber} / ${case.year}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(DividerColor.copy(alpha = 0.4f))
                    )

                    // COLUMN 3: PARTIES
                    Column(modifier = Modifier.weight(5f)) {
                        Text(
                            text = "PARTIES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val partiesText = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)) {
                                append(case.petitioner)
                            }
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = Color(0xFF5F82B5))) {
                                append(" vs ")
                            }
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)) {
                                append(case.respondent)
                            }
                        }
                        Text(
                            text = partiesText,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ROW 2: INTERACTIVE BUTTONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Button
                Surface(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(12.dp),
                    color = SecondarySurfaceColor,
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Delete Button
                Surface(
                    onClick = onDeleteClick,
                    shape = RoundedCornerShape(12.dp),
                    color = SecondarySurfaceColor,
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Status Badge (Pill, Non-dropdown to match design)
                val statusColor = when (case.status) {
                    "Settled" -> SuccessGreen
                    "Not Settled" -> ErrorRed
                    "Registered" -> SuccessGreen
                    else -> WarningOrange
                }
                val statusBg = statusColor.copy(alpha = 0.08f)
                val statusBorder = statusColor.copy(alpha = 0.25f)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusBorder),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
                        )
                        Text(
                            text = case.status,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = statusColor
                        )
                    }
                }

                // Next Date Interactive Selector (replaced Version Dropdown 2.0 with a chevron)
                val context = LocalContext.current
                val nextDateText = if (case.nextDate.isNotEmpty()) {
                    formatToDisplayImage(case.nextDate)
                } else {
                    "Set Next Date"
                }

                Surface(
                    onClick = {
                        if (onNextDateChange != null) {
                            triggerDatePicker(context) { pickedDate ->
                                onNextDateChange(pickedDate)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = SecondarySurfaceColor,
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Next Date Icon",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Next Sitting",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = TextSecondary
                            )
                            Text(
                                text = nextDateText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (case.nextDate.isNotEmpty()) TextPrimary else TextMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Change Date",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CaseCardStatusBadge(status: String) {
    val statusColor = when (status) {
        "Settled" -> SuccessGreen
        "Not Settled" -> ErrorRed
        "Registered" -> SuccessGreen
        else -> WarningOrange
    }

    Surface(
        color = statusColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f)),
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                ),
                color = statusColor
            )
        }
    }
}

// Sliding Status Selector custom component
@Composable
fun ThreeStateToggle(
    state: Int, // -1: Not Settled, 0: Registered, 1: Settled
    onStateChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Case Status",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (state) {
                        -1 -> "Not Settled"
                        1 -> "Settled"
                        else -> "Registered"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = when (state) {
                        -1 -> ErrorRed
                        1 -> SuccessGreen
                        else -> PrimaryBlue
                    }
                )
            }
 
            // 3-position toggle switch track
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(InputFieldColor)
                    .clickable {
                        val nextState = when (state) {
                            0 -> 1    // Middle -> Right
                            1 -> -1   // Right -> Left
                            else -> 0 // Left -> Middle
                        }
                        onStateChange(nextState)
                    }
                    .border(
                        1.dp,
                        DividerColor.copy(alpha = 0.5f),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                // Background division labels
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Not",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (state == -1) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Reg",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (state == 0) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Set",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (state == 1) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Sliding thumb
                val alignment = when (state) {
                    -1 -> Alignment.CenterStart
                    1 -> Alignment.CenterEnd
                    else -> Alignment.Center
                }

                val thumbColor = when (state) {
                    -1 -> ErrorRed
                    1 -> SuccessGreen
                    else -> PrimaryBlue
                }

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .width(60.dp)
                        .fillMaxHeight()
                        .align(alignment)
                        .clip(RoundedCornerShape(18.dp))
                        .background(thumbColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (state) {
                            -1 -> "Not"
                            1 -> "Settled"
                            else -> "Reg"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

// Case creation / revision form Dialog view
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCaseDialog(
    caseToEdit: CaseRecord?,
    suggestedSerialNum: Int,
    onDismiss: () -> Unit,
    onSave: (CaseRecord) -> Unit
) {
    var caseNumber by remember { mutableStateOf(caseToEdit?.caseNumber ?: "") }
    var serialNumber by remember { mutableStateOf(caseToEdit?.serialNumber?.toString() ?: suggestedSerialNum.toString()) }
    var category by remember { mutableStateOf(caseToEdit?.category ?: "MDV") }
    var year by remember { mutableStateOf(caseToEdit?.year ?: "2026") }
    var courtReferredFrom by remember { mutableStateOf(caseToEdit?.courtReferredFrom ?: "District & Sessions Judge") }
    var petitioner by remember { mutableStateOf(caseToEdit?.petitioner ?: "") }
    var petitionerPhone by remember { mutableStateOf(caseToEdit?.petitionerPhone ?: "") }
    var respondent by remember { mutableStateOf(caseToEdit?.respondent ?: "") }
    var respondentPhone by remember { mutableStateOf(caseToEdit?.respondentPhone ?: "") }
    var intakeDate by remember { mutableStateOf(if (caseToEdit != null) formatToDisplay(caseToEdit.intakeDate) else getTodayDisplayDate()) }
    var firstMediationDate by remember { mutableStateOf(if (caseToEdit != null) formatToDisplay(caseToEdit.firstMediationDate) else getTodayDisplayDate()) }
    var reportDate by remember { mutableStateOf(if (caseToEdit != null) formatToDisplay(caseToEdit.reportDate) else "") }
    var mediator by remember { mutableStateOf(caseToEdit?.mediator ?: "SRI ABDUR ROUF BARBHUIYA") }
    
    var statusState by remember {
        mutableStateOf(
            when (caseToEdit?.status) {
                "Settled" -> 1
                "Not Settled" -> -1
                else -> 0
            }
        )
    }

    val context = LocalContext.current

    // Options definitions
    val categoryOptions = listOf(
        "MDV", "PRC", "NI", "CR", "MAC", "TS", "MS", "CS", "FC", "FC CIVIL", 
        "FC CRL", "FC g/A", "GR", "MR", "DV", "LA", "ME", "ME ABBR", "NI CR"
    )
    val yearOptions = listOf(
        "2016", "2017", "2018", "2019", "2020", "2021", "2022", "2023", "2024", "2025", "2026"
    )
    val courtOptions = listOf(
        "District & Sessions Judge", "Addl CJM", "CJM", "Civil Judge Sr. Div. No. 1",
        "Civil Judge Sr. Div. No. 2", "Civil Judge Jr. Div. No. 1", "Civil Judge Jr. Div. No. 2",
        "Civil Judge Jr. Div. No. 3", "Civil Judge Jr. Div. No. 4", "Civil Judge Jr. Div. No. 5",
        "JMFC 1", "JMFC 2", "JMFC 3", "JMFC 4", "SDJM S", "SDJM M", "FAMILY COURT", "MACT"
    )
    val mediatorOptions = listOf(
        "SRI ABDUR ROUF BARBHUIYA", "SRI PANKAJ KANTI DEY", "SRI SAJAL KANTI DEY",
        "SMT SARMISTHA PAUL", "SMT TINKU BAIDYA", "SMT PRATIMA GHOSH",
        "SMT SEEMA CHAKRABORTY", "SRI NILADRI RAY"
    )

    var categoryExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    var courtExpanded by remember { mutableStateOf(false) }
    var mediatorExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCardColor,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Title and Close Button (clean top row, no colorful descriptive banner)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (caseToEdit == null) "Register New Case" else "Edit Case Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Form",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Scrollable Form content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Case Category (Dropdown)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Case Category") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_category_dropdown")
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { categoryExpanded = true }
                        )
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categoryOptions.forEach { curCat ->
                                DropdownMenuItem(
                                    text = { Text(curCat) },
                                    onClick = {
                                        category = curCat
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Case Number (Unrestricted, editable)
                    OutlinedTextField(
                        value = caseNumber,
                        onValueChange = { caseNumber = it },
                        label = { Text("Case Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_case_number"),
                        placeholder = { Text("E.g. 1024") },
                        singleLine = true
                    )

                    // Year (Dropdown - Unrestricted, editable)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = year,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Year") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_year_dropdown")
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { yearExpanded = true }
                        )
                        DropdownMenu(
                            expanded = yearExpanded,
                            onDismissRequest = { yearExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            yearOptions.forEach { curYr ->
                                DropdownMenuItem(
                                    text = { Text(curYr) },
                                    onClick = {
                                        year = curYr
                                        yearExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Court Name (Dropdown)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = courtReferredFrom,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Court Name") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_court_dropdown")
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { courtExpanded = true }
                        )
                        DropdownMenu(
                            expanded = courtExpanded,
                            onDismissRequest = { courtExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            courtOptions.forEach { curCourt ->
                                DropdownMenuItem(
                                    text = { Text(curCourt) },
                                    onClick = {
                                        courtReferredFrom = curCourt
                                        courtExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Informant Name
                    OutlinedTextField(
                        value = petitioner,
                        onValueChange = { petitioner = it },
                        label = { Text("Informant Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_petitioner"),
                        placeholder = { Text("E.g. Gaurav Nath") },
                        singleLine = true
                    )

                    // Informant Phone
                    OutlinedTextField(
                        value = petitionerPhone,
                        onValueChange = { petitionerPhone = it },
                        label = { Text("Informant Phone Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_petitioner_phone"),
                        placeholder = { Text("E.g. 9876543210 or N/A") },
                        singleLine = true
                    )

                    // Respondent Name
                    OutlinedTextField(
                        value = respondent,
                        onValueChange = { respondent = it },
                        label = { Text("Respondent Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_respondent"),
                        placeholder = { Text("E.g. Surajit Dey") },
                        singleLine = true
                    )

                    // Respondent Phone
                    OutlinedTextField(
                        value = respondentPhone,
                        onValueChange = { respondentPhone = it },
                        label = { Text("Defendant / Respondent Phone Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_respondent_phone"),
                        placeholder = { Text("E.g. 9876543210 or N/A") },
                        singleLine = true
                    )

                    // Intake Date
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerDatePicker(context) { pickedDate ->
                                    intakeDate = formatToDisplay(pickedDate)
                                }
                            }
                    ) {
                        OutlinedTextField(
                            value = intakeDate,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Intake Date") },
                            trailingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = "Select Intake Date")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // First Mediation Date
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerDatePicker(context) { pickedDate ->
                                    firstMediationDate = formatToDisplay(pickedDate)
                                }
                            }
                    ) {
                        OutlinedTextField(
                            value = firstMediationDate,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("First Mediation Date") },
                            trailingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = "Select First Mediation Date")
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Date of Fixing and Report
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerDatePicker(context) { pickedDate ->
                                    reportDate = formatToDisplay(pickedDate)
                                }
                            }
                    ) {
                        OutlinedTextField(
                            value = reportDate,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Date of Fixing and Report") },
                            trailingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = "Select Date of Fixing and Report")
                            },
                            placeholder = { Text("Select Date") },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Mediator Name (Dropdown)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = mediator,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mediator Name") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_mediator_dropdown")
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { mediatorExpanded = true }
                        )
                        DropdownMenu(
                            expanded = mediatorExpanded,
                            onDismissRequest = { mediatorExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            mediatorOptions.forEach { curMed ->
                                DropdownMenuItem(
                                    text = { Text(curMed) },
                                    onClick = {
                                        mediator = curMed
                                        mediatorExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Adaptive 3-state Case Status Selector
                    ThreeStateToggle(
                        state = statusState,
                        onStateChange = { statusState = it }
                    )
                }

                // Footer Buttons Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val cleanNo = caseNumber.trim()
                            val cleanPet = petitioner.trim()
                            val cleanRes = respondent.trim()
                            val cleanDate = intakeDate.trim()
                            val cleanSerial = serialNumber.trim().toIntOrNull() ?: suggestedSerialNum

                            if (cleanNo.isEmpty() || cleanPet.isEmpty() || cleanRes.isEmpty() || cleanDate.isEmpty()) {
                                Toast.makeText(context, "Please populate Case Number, Informant, Respondent, and Intake Date!", Toast.LENGTH_LONG).show()
                            } else {
                                val resolvedStatus = when (statusState) {
                                    1 -> "Settled"
                                    -1 -> "Not Settled"
                                    else -> "Registered"
                                }
                                val cleanPetPhone = petitionerPhone.trim().ifEmpty { "N/A" }
                                val cleanResPhone = respondentPhone.trim().ifEmpty { "N/A" }
                                val finalizedCase = CaseRecord(
                                    caseNumber = cleanNo,
                                    year = year,
                                    serialNumber = cleanSerial,
                                    category = category,
                                    courtReferredFrom = courtReferredFrom,
                                    petitioner = cleanPet,
                                    petitionerPhone = cleanPetPhone,
                                    respondent = cleanRes,
                                    respondentPhone = cleanResPhone,
                                    intakeDate = formatToStorage(intakeDate),
                                    firstMediationDate = formatToStorage(firstMediationDate),
                                    reportDate = formatToStorage(reportDate),
                                    mediator = mediator,
                                    status = resolvedStatus
                                )
                                onSave(finalizedCase)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_record_btn")
                    ) {
                        Text(
                            text = if (caseToEdit == null) "Complete Registration" else "Save Record",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, DividerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// Case Detail View content
@Composable
fun CaseDetailContent(
    case: CaseRecord,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    onNextDateChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // Top section: Bento Card of the case itself
            CaseRecordBentoCard(
                case = case,
                isSelected = false,
                isSelectionMode = false,
                onToggleSelected = {},
                onClick = onDismiss, // Clicking card collapses detail view
                onEditClick = onEdit,
                onDeleteClick = onDelete,
                isDetailView = true,
                onStatusChange = onStatusChange,
                onNextDateChange = onNextDateChange
            )

            // Main Case Details Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerColor, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardColor),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // --- CASE DETAILS ---
                    Text(
                        text = "CASE DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = PrimaryBlue
                    )

                    DetailRowWithIcon(
                        icon = Icons.Default.HomeWork,
                        iconColor = PrimaryBlue,
                        iconBgColor = Color(0xFF16253B),
                        title = "Court Jurisdiction",
                        value = case.courtReferredFrom
                    )

                    DetailRowWithIcon(
                        icon = Icons.Default.Person,
                        iconColor = SuccessGreen,
                        iconBgColor = Color(0xFF142921),
                        title = "Mediator",
                        value = case.mediator
                    )

                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 1.dp)

                    // --- IMPORTANT DATES ---
                    Text(
                        text = "IMPORTANT DATES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = PrimaryBlue
                    )

                    ImportantDatesRow(
                        intakeDate = case.intakeDate,
                        firstMediationDate = case.firstMediationDate,
                        reportDate = case.reportDate
                    )

                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 1.dp)

                    // --- PARTY DETAILS ---
                    Text(
                        text = "PARTY DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = PrimaryBlue
                    )

                    PartyDetailRow(
                        title = "Informant / Petitioner",
                        name = case.petitioner,
                        phone = case.petitionerPhone,
                        iconColor = PrimaryBlue,
                        iconBgColor = Color(0xFF16253B)
                    )

                    HorizontalDivider(color = DividerColor.copy(alpha = 0.3f), thickness = 1.dp)

                    PartyDetailRow(
                        title = "Defendant / Respondent",
                        name = case.respondent,
                        phone = case.respondentPhone,
                        iconColor = WarningOrange,
                        iconBgColor = Color(0xFF2C1E14)
                    )

                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 1.dp)

                    // --- CASE STATUS ---
                    CaseStatusDropdownRow(
                        currentStatus = case.status,
                        onStatusSelected = onStatusChange
                    )

                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 1.dp)
                }
            }
        }
    }

@Composable
fun DetailRowWithIcon(
    icon: ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBgColor, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp
                ),
                color = TextPrimary
            )
        }
    }
}

@Composable
fun ImportantDatesRow(
    intakeDate: String,
    firstMediationDate: String,
    reportDate: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateItemCard(
            modifier = Modifier.weight(1f),
            title = "Intake Date",
            date = formatToDisplayImage(intakeDate),
            iconColor = Color(0xFFFF9F43),
            iconBgColor = Color(0xFF2C1E14)
        )
        DateItemCard(
            modifier = Modifier.weight(1f),
            title = "First Mediation",
            date = formatToDisplayImage(firstMediationDate),
            iconColor = Color(0xFF5B8CFF),
            iconBgColor = Color(0xFF16253B)
        )
        DateItemCard(
            modifier = Modifier.weight(1f),
            title = "Report Date",
            date = formatToDisplayImage(reportDate),
            iconColor = Color(0xFF9B7BFF),
            iconBgColor = Color(0xFF22163B)
        )
    }
}

@Composable
fun DateItemCard(
    modifier: Modifier,
    title: String,
    date: String,
    iconColor: Color,
    iconBgColor: Color
) {
    Row(
        modifier = modifier
            .background(Color(0xFF0F1722), shape = RoundedCornerShape(8.dp))
            .border(1.dp, DividerColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBgColor, shape = RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PartyDetailRow(
    title: String,
    name: String,
    phone: String,
    iconColor: Color,
    iconBgColor: Color
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBgColor, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = name.ifEmpty { "N/A" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = TextPrimary
            )
            if (phone.isNotEmpty() && phone != "N/A") {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        if (phone.isNotEmpty() && phone != "N/A") {
            IconButton(
                onClick = {
                    try {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(dialIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot dial $phone", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(32.dp)
                    .border(1.dp, DividerColor, RoundedCornerShape(50.dp))
                    .background(SecondarySurfaceColor, RoundedCornerShape(50.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Call $name",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CaseStatusDropdownRow(
    currentStatus: String,
    onStatusSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val statuses = listOf("Registered", "Settled", "Not Settled", "Mediation 1.0")
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "CASE STATUS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            ),
            color = PrimaryBlue
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(InputFieldColor, RoundedCornerShape(8.dp))
                    .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusColor = getStatusColor(currentStatus)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, RoundedCornerShape(50.dp))
                    )
                    Text(
                        text = currentStatus,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp
                        ),
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand Status Dropdown",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(SurfaceCardColor)
                    .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
            ) {
                statuses.forEach { statusOption ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val statusColor = getStatusColor(statusOption)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(statusColor, RoundedCornerShape(50.dp))
                                )
                                Text(
                                    text = statusOption,
                                    color = if (statusOption == currentStatus) PrimaryBlue else TextPrimary
                                )
                            }
                        },
                        onClick = {
                            onStatusSelected(statusOption)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

fun formatToDisplayImage(dateStr: String): String {
    if (dateStr.isEmpty()) return "N/A"
    val parts = dateStr.trim().split("-")
    if (parts.size == 3) {
        val y = parts[0]
        val m = parts[1]
        val d = parts[2]
        val monthAbbr = when (m) {
            "01", "1" -> "Jan"
            "02", "2" -> "Feb"
            "03", "3" -> "Mar"
            "04", "4" -> "Apr"
            "05", "5" -> "May"
            "06", "6" -> "Jun"
            "07", "7" -> "Jul"
            "08", "8" -> "Aug"
            "09", "9" -> "Sep"
            "10" -> "Oct"
            "11" -> "Nov"
            "12" -> "Dec"
            else -> m
        }
        val dayFormatted = if (d.length == 1) "0$d" else d
        return "$dayFormatted $monthAbbr $y"
    }
    return dateStr
}

// Single bento item formatter helper with copy utility built-in
@Composable
fun DetailRow(label: String, value: String, isCode: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = if (isCode) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

// Dialog helper triggering native DatePickerDialog
fun triggerDatePicker(context: Context, onDateSelected: (String) -> Unit) {
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            onDateSelected(formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.show()
}

// TSV serialization compiler
fun exportToTsv(cases: List<CaseRecord>): String {
    val header = "Serial_No\tCase_Number\tCategory\tCourt_Referred_From\tPetitioner\tRespondent\tIntake_Date\tMediator\tStatus\n"
    val rows = cases.joinToString("\n") { case ->
        "${case.serialNumber}\t${case.caseNumber}\t${case.category}\t${case.courtReferredFrom}\t${case.petitioner}\t${case.respondent}\t${case.intakeDate}\t${case.mediator}\t${case.status}"
    }
    return header + rows
}

// Status Badges
@Composable
fun StatusBadge(status: String) {
    val badgeColor = getStatusColor(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = badgeColor
        )
    }
}

// Color mapper according to status value
fun getStatusColor(status: String): Color {
    return when (status) {
        "Settled" -> SuccessGreen
        "Not Settled" -> ErrorRed
        "Mediation 1.0" -> AccentPurple
        else -> PrimaryBlue
    }
}

fun getMonthOrder(month: String): Int {
    return when (month) {
        "January" -> 1
        "February" -> 2
        "March" -> 3
        "April" -> 4
        "May" -> 5
        "June" -> 6
        "July" -> 7
        "August" -> 8
        "September" -> 9
        "October" -> 10
        "November" -> 11
        "December" -> 12
        else -> 13
    }
}

fun formatToDisplay(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    val parts = dateStr.trim().split("-")
    if (parts.size == 3) {
        // YYYY-MM-DD -> DD-MM-YYYY
        val y = parts[0]
        val m = parts[1]
        val d = parts[2]
        return "$d-$m-$y"
    }
    return dateStr
}

fun formatToStorage(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    val parts = dateStr.trim().split("-")
    if (parts.size == 3) {
        val p1 = parts[0]
        val p2 = parts[1]
        val p3 = parts[2]
        if (p1.length == 2 && p3.length == 4) {
            // DD-MM-YYYY -> YYYY-MM-DD
            return "$p3-$p2-$p1"
        }
    }
    return dateStr
}

fun getTodayDisplayDate(): String {
    val calendar = Calendar.getInstance()
    val y = calendar.get(Calendar.YEAR)
    val m = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
    val d = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
    return "$d-$m-$y"
}

