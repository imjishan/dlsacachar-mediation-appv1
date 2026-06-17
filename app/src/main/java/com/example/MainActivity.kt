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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.DLSADeepNavy
import com.example.ui.theme.DLSASlateBlue
import com.example.ui.theme.DLSASteelGray
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
            val year = record.year
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
                    modifier = Modifier.width(310.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    SideDrawerContent(
                        folderStructure = folderStructure,
                        currentFilter = currentFilter,
                        directoryUri = directoryUriState.value,
                        totalCount = casesState.value.size,
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
                                    color = Color.White
                                )
                                Text(
                                    text = when (val filter = currentFilter) {
                                        is Filter.All -> "All Records (${casesState.value.size})"
                                        is Filter.Status -> "Filtered: ${filter.status}"
                                        is Filter.MonthYear -> "Folder: ${filter.month} ${filter.year}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Drawer Menu",
                                    tint = Color.White
                                )
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
                                        tint = Color.White
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
                                        tint = Color.White
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
                                        tint = MaterialTheme.colorScheme.errorContainer
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DLSADeepNavy
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            caseToEdit = null
                            showAddEditDialog = true
                        },
                        containerColor = DLSADeepNavy,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("add_case_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Register New Case",
                            tint = Color.White
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
                        casesState.value.filter { case ->
                            val matchesFilter = when (val filter = currentFilter) {
                                is Filter.All -> true
                                is Filter.Status -> case.status.equals(filter.status, ignoreCase = true)
                                is Filter.MonthYear -> {
                                    val yr = case.year
                                    val mon = CaseRecordMapper.getMonthFromDate(case.intakeDate)
                                    yr == filter.year && mon.equals(filter.month, ignoreCase = true)
                                }
                            }

                            val matchesSearch = if (searchText.isBlank()) {
                                true
                            } else {
                                case.caseNumber.contains(searchText, ignoreCase = true) ||
                                        case.petitioner.contains(searchText, ignoreCase = true) ||
                                        case.respondent.contains(searchText, ignoreCase = true) ||
                                        case.mediator.contains(searchText, ignoreCase = true)
                            }

                            matchesFilter && matchesSearch
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search text field
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp
                        ) {
                            TextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholder = { Text("Search by No., Petitioner, Opponent, Mediator...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                                trailingIcon = {
                                    if (searchText.isNotEmpty()) {
                                        IconButton(onClick = { searchText = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_field_case")
                                    .height(56.dp)
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

                        if (isLoadingState.value) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = DLSADeepNavy)
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
        AddEditCaseDialog(
            caseToEdit = caseToEdit,
            suggestedSerialNum = viewModel.getNextSerialNumber(),
            onDismiss = { showAddEditDialog = false },
            onSave = { updatedCase ->
                viewModel.saveCaseRecord(updatedCase) { success, message ->
                    if (success) {
                        showAddEditDialog = false
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Case Detail Modal
    if (showCaseDetailDialog != null) {
        val detailCase = showCaseDetailDialog!!
        CaseDetailDialog(
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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_purge_btn")
                ) {
                    Text("DELETE ALL FILES")
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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_bulk_btn")
                ) {
                    Text("BULK DELETE")
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

// Side Navigation Drawer Content
@Composable
fun SideDrawerContent(
    folderStructure: Map<String, List<String>>,
    currentFilter: Filter,
    directoryUri: String?,
    totalCount: Int,
    onFilterSelected: (Filter) -> Unit,
    onClearDirectory: () -> Unit,
    onRefresh: () -> Unit,
    expandedYears: Set<String>,
    onToggleYear: (String) -> Unit,
    onTriggerPurge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Legal header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DLSADeepNavy)
                .padding(top = 40.dp, bottom = 20.dp, start = 16.dp, end = 16.dp)
        ) {
            Column {
                Icon(
                    imageVector = Icons.Default.Balance,
                    contentDescription = "Scales of Justice",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CACHAR DISTRICT LEGAL SERVICES",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "AUTHORITY, SILCHAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Case Records Registry",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        // Active database path visual indicator
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Active Folder Icon",
                        tint = DLSADeepNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Linked Registry Folder",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Reload Files",
                        tint = DLSADeepNavy,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val friendlyPath = directoryUri?.substringAfterLast("%3A")?.replace("%2F", "/") ?: "None"
            Text(
                text = "Path: $friendlyPath",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onClearDirectory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Detach Folder", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Scrollable tree & filter section
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            // General shortcut
            NavigationDrawerItem(
                label = { Text("All Cases Registry ($totalCount)") },
                selected = currentFilter is Filter.All,
                onClick = { onFilterSelected(Filter.All) },
                icon = { Icon(Icons.AutoMirrored.Default.List, contentDescription = null) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Dynamic case year/month folders
            Text(
                text = "CASE DIRECTORIES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
            )

            if (folderStructure.isEmpty()) {
                Text(
                    text = "No folders. Register cases to group them dynamically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            } else {
                folderStructure.forEach { (year, months) ->
                    val isYearExpanded = expandedYears.contains(year)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleYear(year) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isYearExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = DLSASlateBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Year $year Folder",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isYearExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
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
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelectedFolder) DLSASlateBlue.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable { onFilterSelected(Filter.MonthYear(year, monthName)) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = DLSASteelGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = monthName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelectedFolder) DLSADeepNavy else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Status shortcut Section
            Text(
                text = "STATUS SHORTCUTS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
            )

            listOf("Registered", "Settled", "Not Settled", "Mediation 1.0").forEach { statusName ->
                val isSelectedStatus = currentFilter is Filter.Status && currentFilter.status == statusName
                NavigationDrawerItem(
                    label = { Text(statusName) },
                    selected = isSelectedStatus,
                    onClick = { onFilterSelected(Filter.Status(statusName)) },
                    icon = {
                        val statusIcon = when (statusName) {
                            "Settled" -> Icons.Default.CheckCircle
                            "Not Settled" -> Icons.Default.Cancel
                            "Mediation 1.0" -> Icons.Default.Gavel
                            else -> Icons.Default.HourglassEmpty
                        }
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = getStatusColor(statusName)
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }

        // Bottom settings/purging tools
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = onTriggerPurge,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("purge_button_trigger")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Purge Workspace", style = MaterialTheme.typography.labelMedium)
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
            .background(DLSADeepNavy)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Balance,
                    contentDescription = "Scales of Justice logo",
                    tint = DLSADeepNavy,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CACHAR DISTRICT LEGAL SERVICES AUTHORITY",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = DLSADeepNavy
                )
                Text(
                    text = "SILCHAR, ASSAM",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = DLSASlateBlue
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Welcome to the Cachar DLSA Case Records Registry",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please select or create an empty folder on your physical storage to initialize your offline registry database. Case files are persisted strictly locally inside this designated folder.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onSelectDirectory,
                    colors = ButtonDefaults.buttonColors(containerColor = DLSADeepNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboard_dir_btn")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Repository Folder", color = Color.White, fontWeight = FontWeight.Bold)
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelected() },
                            modifier = Modifier.testTag("checkbox_${case.caseNumber}")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = case.caseNumber,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = DLSADeepNavy
                    )
                }

                StatusBadge(status = case.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bento division: parties
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Petitioner/Informant:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DLSASlateBlue,
                            modifier = Modifier.width(130.dp)
                        )
                        Text(
                            text = case.petitioner,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Respondent/Opponent:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DLSASlateBlue,
                            modifier = Modifier.width(130.dp)
                        )
                        Text(
                            text = case.respondent,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row for metadata (Category, Serial and Date)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = DLSASteelGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = case.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = DLSASteelGray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Serial No: ${case.serialNumber}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Intake Calendar Indicator",
                        tint = DLSASteelGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = case.intakeDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                    text = "CASE STATUS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (state) {
                        -1 -> "NOT SETTLED"
                        1 -> "SETTLED"
                        else -> "REGISTERED"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = when (state) {
                        -1 -> MaterialTheme.colorScheme.error
                        1 -> Color(0xFF4CAF50)
                        else -> DLSADeepNavy
                    }
                )
            }

            // 3-position toggle switch track
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
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
                        text = "NOT",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (state == -1) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "REG",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (state == 0) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "SET",
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
                    -1 -> MaterialTheme.colorScheme.error
                    1 -> Color(0xFF4CAF50) // settled green
                    else -> DLSADeepNavy // registered navy
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
                            -1 -> "NOT"
                            1 -> "SETTLED"
                            else -> "REG"
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
    var respondent by remember { mutableStateOf(caseToEdit?.respondent ?: "") }
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DLSADeepNavy)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Register New Mediation Case",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enter the case details below to register it in the DLSA system.",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close Form", tint = Color.White)
                        }
                    }
                }

                // Scrollable Form content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Row 1: Case Category (Dropdown) & Case Number
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = categoryExpanded,
                                onExpandedChange = { categoryExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = category,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Case Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("field_category_dropdown"),
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false }
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
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = caseNumber,
                                onValueChange = { caseNumber = it },
                                label = { Text("Case Number") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("field_case_number"),
                                placeholder = { Text("E.g. 1024") },
                                singleLine = true,
                                enabled = caseToEdit == null // immutable identifier key
                            )
                        }
                    }

                    // Row 2: Year (Dropdown) & Court Name (Dropdown)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = yearExpanded,
                                onExpandedChange = { yearExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = year,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Year") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("field_year_dropdown"),
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    enabled = caseToEdit == null // determine primary filename
                                )
                                ExposedDropdownMenu(
                                    expanded = yearExpanded,
                                    onDismissRequest = { yearExpanded = false }
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
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = courtExpanded,
                                onExpandedChange = { courtExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = courtReferredFrom,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Court Name") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courtExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("field_court_dropdown"),
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = courtExpanded,
                                    onDismissRequest = { courtExpanded = false }
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
                        }
                    }

                    // Row 3: Informant Name & Respondent Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
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
                        }

                        Box(modifier = Modifier.weight(1f)) {
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
                        }
                    }

                    // Row 4: Intake Date (Datepicker) & First Mediation Date (Datepicker)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
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
                    }

                    // Row 5: Date of Fixing and Report (Datepicker) & Mediator Name (Dropdown)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
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

                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = mediatorExpanded,
                                onExpandedChange = { mediatorExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = mediator,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Mediator Name") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mediatorExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("field_mediator_dropdown"),
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded = mediatorExpanded,
                                    onDismissRequest = { mediatorExpanded = false }
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
                                val finalizedCase = CaseRecord(
                                    caseNumber = cleanNo,
                                    year = year,
                                    serialNumber = cleanSerial,
                                    category = category,
                                    courtReferredFrom = courtReferredFrom,
                                    petitioner = cleanPet,
                                    respondent = cleanRes,
                                    intakeDate = formatToStorage(intakeDate),
                                    firstMediationDate = formatToStorage(firstMediationDate),
                                    reportDate = formatToStorage(reportDate),
                                    mediator = mediator,
                                    status = resolvedStatus
                                )
                                onSave(finalizedCase)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DLSADeepNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_record_btn")
                    ) {
                        Text(
                            text = if (caseToEdit == null) "Complete Registration" else "Save Record",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

// Case Detail View dialog
@Composable
fun CaseDetailDialog(
    case: CaseRecord,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DLSADeepNavy)
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "FILE INSPECTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            StatusBadge(status = case.status)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = case.caseNumber,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                // Table detail lines
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailRow("Serial Registry Code", case.serialNumber.toString(), true)
                    DetailRow("Case Category", case.category, false)
                    DetailRow("Year", case.year, false)
                    DetailRow("Court Name", case.courtReferredFrom.ifEmpty { "Not specified" }, false)
                    DetailRow("Petitioner / Informant", case.petitioner, false)
                    DetailRow("Respondent / Opposite Party", case.respondent, false)
                    DetailRow("Intake / Register Date", formatToDisplay(case.intakeDate), false)
                    DetailRow("First Mediation Date", formatToDisplay(case.firstMediationDate), false)
                    DetailRow("Date of Fixing and Report", formatToDisplay(case.reportDate).ifEmpty { "Not specified" }, false)
                    DetailRow("Assigned Mediator", case.mediator, false)
                }

                // Action controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_case_btn")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onEdit,
                            colors = ButtonDefaults.buttonColors(containerColor = DLSASlateBlue),
                            modifier = Modifier.testTag("edit_case_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Record", color = Color.White)
                        }
                    }
                }
            }
        }
    }
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
            color = DLSASteelGray
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
        "Settled" -> Color(0xFF2E7D32) // Pure green
        "Not Settled" -> Color(0xFFC62828) // Strong red
        "Mediation 1.0" -> Color(0xFF0277BD) // Rich blue
        else -> Color(0xFFEF6C00) // Dark amber / orange for Registered
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

