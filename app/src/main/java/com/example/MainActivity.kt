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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.Logout
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
    val tasksState = viewModel.tasks.collectAsState()

    var currentFilter by remember { mutableStateOf<Filter>(Filter.All) }
    var searchText by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var showTasksScreen by remember { mutableStateOf(false) }
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

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = with(density) { 110.dp.toPx() }
    var currentDetailOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(showCaseDetailDialog) {
        if (showCaseDetailDialog != null) {
            currentDetailOffsetY = 0f
        }
    }

    val detailHeaderDragModifier = Modifier.draggable(
        state = rememberDraggableState { delta ->
            currentDetailOffsetY = (currentDetailOffsetY + delta).coerceAtLeast(0f)
        },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            scope.launch {
                if (currentDetailOffsetY > dismissThresholdPx || velocity > 600f) {
                    animate(
                        initialValue = currentDetailOffsetY,
                        targetValue = screenHeightPx,
                        animationSpec = tween(180)
                    ) { value, _ -> currentDetailOffsetY = value }
                    showCaseDetailDialog = null
                    currentDetailOffsetY = 0f
                } else {
                    animate(
                        initialValue = currentDetailOffsetY,
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) { value, _ -> currentDetailOffsetY = value }
                }
            }
        }
    )

    val detailNestedScrollConnection = remember(screenHeightPx, dismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && currentDetailOffsetY > 0f) {
                    val consumed = delta.coerceAtLeast(-currentDetailOffsetY)
                    currentDetailOffsetY += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f) {
                    currentDetailOffsetY += delta
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentDetailOffsetY > 0f) {
                    if (currentDetailOffsetY > dismissThresholdPx || available.y > 600f) {
                        animate(
                            initialValue = currentDetailOffsetY,
                            targetValue = screenHeightPx,
                            animationSpec = tween(180)
                        ) { value, _ -> currentDetailOffsetY = value }
                        showCaseDetailDialog = null
                        currentDetailOffsetY = 0f
                    } else {
                        animate(
                            initialValue = currentDetailOffsetY,
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { value, _ -> currentDetailOffsetY = value }
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 600f) {
                    animate(
                        initialValue = currentDetailOffsetY,
                        targetValue = screenHeightPx,
                        animationSpec = tween(180)
                    ) { value, _ -> currentDetailOffsetY = value }
                    showCaseDetailDialog = null
                    currentDetailOffsetY = 0f
                    return available
                }
                return Velocity.Zero
            }
        }
    }

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
                    drawerContainerColor = Color(0xFF090E17)
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
                        },
                        tasks = tasksState.value,
                        onOpenTasks = {
                            scope.launch { drawerState.close() }
                            showTasksScreen = true
                        },
                        onCloseDrawer = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Button (Menu)
                            Surface(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = SecondarySurfaceColor,
                                border = BorderStroke(1.dp, DividerColor),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open Drawer Menu",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Center Title / Subtitle
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cachar DLSA Registry",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (val filter = currentFilter) {
                                        is Filter.All -> "All Records • ${casesState.value.size} Case${if (casesState.value.size == 1) "" else "s"}"
                                        is Filter.Status -> "Filtered • ${filter.status}"
                                        is Filter.MonthYear -> "Folder • ${filter.month} ${filter.year}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = TextMuted
                                )
                            }

                            // Delete selected button
                            if (isSelectionMode && selectedCases.isNotEmpty()) {
                                Surface(
                                    onClick = {
                                        showMultiDeleteConfirmation = true
                                        multiDeleteConfirmationInput = ""
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Selected Records",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        if (showCaseDetailDialog == null) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors = listOf(PrimaryBlue, Color(0xFF635BFF))
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        caseToEdit = null
                                        showAddEditDialog = true
                                    }
                                    .testTag("add_case_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Register New Case",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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

                        // 1. The Main Content (Search bar, Loading/Empty stats, LazyColumn list of cases)
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
                                                    tint = PrimaryBlue
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

                            if (isLoadingState.value) {
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

                // 2. The Case Detail View (overlaying full screen, slide down to minimize)
                AnimatedVisibility(
                    visible = showCaseDetailDialog != null,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(animationSpec = tween(250)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val detailCase = showCaseDetailDialog
                    if (detailCase != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(0, currentDetailOffsetY.roundToInt()) }
                                .nestedScroll(detailNestedScrollConnection)
                                .background(MaterialTheme.colorScheme.background)
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        ) {
                            BackHandler(enabled = true) {
                                showCaseDetailDialog = null
                            }

                            // 1. Drag handle (Slide down indicator)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(detailHeaderDragModifier)
                                    .padding(top = 10.dp, bottom = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(5.dp)
                                        .background(
                                            color = DividerColor.copy(alpha = 0.9f),
                                            shape = RoundedCornerShape(2.5.dp)
                                        )
                                )
                            }

                            var showDetailMoreMenu by remember { mutableStateOf(false) }

                            // 2. Case Details Top Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(detailHeaderDragModifier)
                                    .padding(start = 8.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showCaseDetailDialog = null }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Minimize Case",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Case Details",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        ),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Cachar DLSA Registry • Slide down to minimize",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        color = TextSecondary
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showDetailMoreMenu = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More Options",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showDetailMoreMenu,
                                        onDismissRequest = { showDetailMoreMenu = false },
                                        modifier = Modifier
                                            .background(SurfaceCardColor)
                                            .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = null,
                                                    tint = PrimaryBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            text = { Text("Edit Case Record", color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                showDetailMoreMenu = false
                                                caseToEdit = detailCase
                                                showCaseDetailDialog = null
                                                showAddEditDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = ErrorRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            text = { Text("Delete Case Record", color = ErrorRed, fontSize = 13.sp) },
                                            onClick = {
                                                showDetailMoreMenu = false
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
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = null,
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            text = { Text("Copy TSV Data", color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                showDetailMoreMenu = false
                                                val tsvText = """
                                                    Case Number	Year	Category	Court	Petitioner	Respondent	Status
                                                    ${detailCase.caseNumber}	${detailCase.year}	${detailCase.category}	${detailCase.courtReferredFrom}	${detailCase.petitioner}	${detailCase.respondent}	${detailCase.status}
                                                """.trimIndent()
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Case TSV", tsvText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "TSV copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = null,
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            text = { Text("Share Case Summary", color = Color.White, fontSize = 13.sp) },
                                            onClick = {
                                                showDetailMoreMenu = false
                                                val shareText = "Case: ${detailCase.caseNumber}/${detailCase.year} (${detailCase.category})\nCourt: ${detailCase.courtReferredFrom}\nMediator: ${detailCase.mediator}\nPetitioner: ${detailCase.petitioner}\nRespondent: ${detailCase.respondent}\nStatus: ${detailCase.status}"
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Case Details"))
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = DividerColor,
                                thickness = 1.dp
                            )

                            // 3. Scrollable Detail Content
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
                                onOpenMoreOptions = { showDetailMoreMenu = true },
                                tasks = tasksState.value.filter { it.caseNumber == detailCase.caseNumber },
                                onAddTask = { note ->
                                    viewModel.addTask(detailCase, note) {
                                        Toast.makeText(context, "Task created for Case ${detailCase.caseNumber}!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onToggleTask = { taskId ->
                                    viewModel.toggleTask(taskId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
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

    // Tasks and Notes Screen
    if (showTasksScreen) {
        TasksScreen(
            tasks = tasksState.value,
            onToggleTask = { viewModel.toggleTask(it) },
            onDeleteTask = { viewModel.deleteTask(it) },
            onAddTask = { note ->
                viewModel.addTask(null, note) {
                    Toast.makeText(context, "Task created!", Toast.LENGTH_SHORT).show()
                }
            },
            onBack = { showTasksScreen = false }
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
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF132238) else Color.Transparent,
        label = "BgColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .height(46.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
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
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            // Optional Badge Count
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF152238), shape = androidx.compose.foundation.shape.CircleShape)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Optional Arrow chevron
            if (showArrow) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
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
    onTriggerPurge: () -> Unit,
    tasks: List<TaskItem> = emptyList(),
    onOpenTasks: () -> Unit = {},
    onCloseDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val darkDrawerBg = Color(0xFF090E17)
    val darkCardBg = Color(0xFF132238)
    val mutedText = Color(0xFF64748B)
    val subTextColor = Color(0xFF94A3B8)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkDrawerBg)
    ) {
        // 1. TOP HEADER SECTION
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Left Scales of Justice Icon Box
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(darkCardBg, shape = RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Balance,
                        contentDescription = "DLSA Logo",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DLSA Cachar",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Badge",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Mediation Centre, Cachar",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = subTextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Case Records Registry",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        ),
                        color = mutedText
                    )
                }
            }

            // Upward collapse button on top right
            IconButton(
                onClick = onCloseDrawer,
                modifier = Modifier
                    .size(36.dp)
                    .background(darkCardBg, shape = RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Collapse Drawer",
                    tint = subTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. SCROLLABLE MIDDLE CONTENT (OVERVIEW, TOOLS, DIRECTORIES)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            // --- SECTION 1: OVERVIEW ---
            Text(
                text = "OVERVIEW",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 11.sp
                ),
                color = mutedText,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )

            // All Cases
            val allCasesCount = cases.size
            CustomDrawerItem(
                label = "All Cases",
                selected = currentFilter is Filter.All,
                onClick = { onFilterSelected(Filter.All) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = subTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                },
                badge = allCasesCount.toString(),
                showArrow = true
            )

            // Settled
            val settledCount = cases.count { it.status.equals("Settled", ignoreCase = true) }
            CustomDrawerItem(
                label = "Settled",
                selected = currentFilter is Filter.Status && currentFilter.status.equals("Settled", ignoreCase = true),
                onClick = { onFilterSelected(Filter.Status("Settled")) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981), // Bright Green
                        modifier = Modifier.size(20.dp)
                    )
                },
                badge = settledCount.toString(),
                showArrow = true
            )

            // Not Settled
            val notSettledCount = cases.count { it.status.equals("Not Settled", ignoreCase = true) }
            CustomDrawerItem(
                label = "Not Settled",
                selected = currentFilter is Filter.Status && currentFilter.status.equals("Not Settled", ignoreCase = true),
                onClick = { onFilterSelected(Filter.Status("Not Settled")) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color(0xFFEF4444), // Bright Red
                        modifier = Modifier.size(20.dp)
                    )
                },
                badge = notSettledCount.toString(),
                showArrow = true
            )

            // Pending
            val pendingCount = cases.count { 
                it.status.equals("Pending", ignoreCase = true) || 
                it.status.equals("Registered", ignoreCase = true) || 
                it.status.equals("Mediation 1.0", ignoreCase = true) 
            }
            CustomDrawerItem(
                label = "Pending",
                selected = currentFilter is Filter.Status && (
                    currentFilter.status.equals("Registered", ignoreCase = true) || 
                    currentFilter.status.equals("Pending", ignoreCase = true)
                ),
                onClick = { onFilterSelected(Filter.Status("Registered")) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6), // Bright Purple
                        modifier = Modifier.size(20.dp)
                    )
                },
                badge = pendingCount.toString(),
                showArrow = true
            )

            HorizontalDivider(
                color = Color(0xFF1E293B),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )

            // --- SECTION 2: TOOLS ---
            Text(
                text = "TOOLS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 11.sp
                ),
                color = mutedText,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )

            // Tasks & Notes
            val pendingTasksCount = tasks.count { !it.isCompleted }
            CustomDrawerItem(
                label = "Tasks & Notes",
                selected = false,
                onClick = onOpenTasks,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        tint = if (pendingTasksCount > 0) WarningOrange else subTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                },
                badge = if (pendingTasksCount > 0) pendingTasksCount.toString() else null,
                showArrow = true
            )

            // Calendar
            CustomDrawerItem(
                label = "Calendar",
                selected = false,
                onClick = {
                    Toast.makeText(context, "Opening Legal Calendar...", Toast.LENGTH_SHORT).show()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = subTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                },
                showArrow = true
            )

            // Reports
            CustomDrawerItem(
                label = "Reports",
                selected = false,
                onClick = {
                    Toast.makeText(context, "Generating Cases Summary Report...", Toast.LENGTH_SHORT).show()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = subTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                },
                showArrow = true
            )

            // Templates
            CustomDrawerItem(
                label = "Templates",
                selected = false,
                onClick = {
                    Toast.makeText(context, "Opening Legal Document Templates...", Toast.LENGTH_SHORT).show()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WorkOutline,
                        contentDescription = null,
                        tint = subTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                },
                showArrow = true
            )

            // --- DYNAMIC FOLDER DIRECTORIES (Preserving folder structure feature) ---
            if (folderStructure.isNotEmpty()) {
                HorizontalDivider(
                    color = Color(0xFF1E293B),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )

                Text(
                    text = "CASE DIRECTORIES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.sp
                    ),
                    color = mutedText,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                )

                folderStructure.forEach { (year, months) ->
                    val isYearExpanded = expandedYears.contains(year)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleYear(year) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isYearExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isYearExpanded) PrimaryBlue else subTextColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Year $year Folder",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isYearExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = subTextColor,
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
                                                tint = if (isSelectedFolder) PrimaryBlue else subTextColor,
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

            // Linked Registry Folder option (if folder attached)
            if (directoryUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(darkCardBg, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val friendlyPath = directoryUri.substringAfterLast("%3A").replace("%2F", "/")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = friendlyPath,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = subTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onClearDirectory,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Detach Folder",
                            tint = ErrorRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 3. TIP OF THE DAY CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(
                    color = Color(0xFF162B54),
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    Toast.makeText(context, "Tip: Use search & status filters for faster retrieval!", Toast.LENGTH_SHORT).show()
                }
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF0F1B36), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = "Tip Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Tip of the Day",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Keep your case records updated for better tracking.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        ),
                        color = subTextColor
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = subTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. USER PROFILE BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Initial Avatar Circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(PrimaryBlue, shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SA",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Subhadra Acharyya",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Secretary",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        color = subTextColor
                    )
                }
            }

            // Logout Button Box
            IconButton(
                onClick = onTriggerPurge,
                modifier = Modifier
                    .size(38.dp)
                    .background(darkCardBg, shape = RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = "Logout",
                    tint = subTextColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 5. FOOTER BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "v2.6.0",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = mutedText
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(Color(0xFF1E293B))
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        Toast.makeText(context, "Dark Mode active", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = subTextColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Dark Mode",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = subTextColor
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(Color(0xFF1E293B))
            )

            IconButton(
                onClick = {
                    Toast.makeText(context, "Settings coming soon!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = subTextColor,
                    modifier = Modifier.size(16.dp)
                )
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    onNextDateChange: ((String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val cardBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        label = "BorderWidth"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryBlue else Color(0xFF1F355C),
        label = "BorderColor"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isSelected) 1.015f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "CardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = cardBorderWidth,
                color = cardBorderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCardColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelected() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryBlue,
                        uncheckedColor = Color.Gray
                    ),
                    modifier = Modifier
                        .testTag("checkbox_${case.caseNumber}")
                        .padding(end = 8.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // ROW 1: TAGS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Tag (PRC)
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1B2E53), shape = RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = case.category.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    // Status Badge (Interactive dropdown when onStatusChange is provided)
                    val statusColor = when (case.status) {
                        "Settled" -> SuccessGreen
                        "Not Settled" -> ErrorRed
                        "Registered" -> SuccessGreen
                        else -> WarningOrange
                    }
                    val statusBg = when (case.status) {
                        "Settled" -> Color(0xFF142921)
                        "Not Settled" -> Color(0xFF2C1E1D)
                        "Registered" -> Color(0xFF142921)
                        else -> Color(0xFF2C241E)
                    }

                    var statusMenuExpanded by remember { mutableStateOf(false) }
                    val availableStatuses = listOf("Registered", "Settled", "Not Settled", "Mediation 1.0")

                    Box {
                        Box(
                            modifier = Modifier
                                .background(statusBg, shape = RoundedCornerShape(50))
                                .border(1.dp, statusColor.copy(alpha = 0.25f), shape = RoundedCornerShape(50))
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = onStatusChange != null) {
                                    statusMenuExpanded = true
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
                                )
                                Text(
                                    text = case.status.ifEmpty { "Registered" },
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                if (onStatusChange != null) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Status",
                                        tint = statusColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (onStatusChange != null) {
                            DropdownMenu(
                                expanded = statusMenuExpanded,
                                onDismissRequest = { statusMenuExpanded = false },
                                modifier = Modifier
                                    .background(SurfaceCardColor)
                                    .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                            ) {
                                availableStatuses.forEach { opt ->
                                    val optColor = when (opt) {
                                        "Settled" -> SuccessGreen
                                        "Not Settled" -> ErrorRed
                                        "Registered" -> SuccessGreen
                                        else -> WarningOrange
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(optColor, shape = androidx.compose.foundation.shape.CircleShape)
                                                )
                                                Text(
                                                    text = opt,
                                                    color = if (case.status.equals(opt, ignoreCase = true)) optColor else Color.White,
                                                    fontWeight = if (case.status.equals(opt, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            statusMenuExpanded = false
                                            onStatusChange(opt)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ROW 2: CASE NUMBER
                Text(
                    text = "${case.caseNumber} / ${case.year}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ROW 3: PARTIES (with FlowRow support for wrapping)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = case.petitioner,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1B2E53), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = "vs",
                            color = Color(0xFF5B8CFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = case.respondent,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ROW 4: DATES SUB-CARDS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DateSubCard(
                        label = "Intake Date",
                        dateValue = case.intakeDate,
                        iconColor = WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                    DateSubCard(
                        label = "First Mediation",
                        dateValue = case.firstMediationDate,
                        iconColor = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    DateSubCard(
                        label = "Report Date",
                        dateValue = case.reportDate,
                        iconColor = AccentPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DateSubCard(
    label: String,
    dateValue: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    val displayDate = if (dateValue.isNotEmpty()) formatToDisplayImage(dateValue) else "N/A"
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF111926), // Sleek lighter dark card container matching design
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(13.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    val focusManager = LocalFocusManager.current

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
        "Civil Judge Jr Div, Lakhipur",
        "JMFC 1", "JMFC 2", "JMFC 3", "JMFC 4", "SDJM S", "SDJM M", "FAMILY COURT", "MACT",
        "FTC, Cachar"
    )
    val mediatorOptions = listOf(
        "SRI ABDUR ROUF BARBHUIYA", "SRI PANKAJ KANTI DEY", "SRI SAJAL KANTI DEY",
        "SMT SARMISTHA PAUL", "SMT TINKU BAIDYA", "SMT PRATIMA GHOSH",
        "SMT SEEMA CHAKRABORTY", "SRI NILADRI RAY",
        "Smt. Purnima Bhattacharjee", "Sri. Mohitosh Das"
    )

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
                    // Case Category (System Dropdown)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerSystemOptionPicker(
                                    context = context,
                                    title = "Select Case Category",
                                    options = categoryOptions,
                                    currentValue = category,
                                    onOptionSelected = { category = it }
                                )
                            }
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Case Category") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Category") },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_category_dropdown")
                        )
                    }

                    // Case Number (Unrestricted, editable)
                    OutlinedTextField(
                        value = caseNumber,
                        onValueChange = { caseNumber = it },
                        label = { Text("Case Number") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_case_number"),
                        placeholder = { Text("E.g. 1024") },
                        singleLine = true
                    )

                    // Year (System Dropdown)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerSystemOptionPicker(
                                    context = context,
                                    title = "Select Year",
                                    options = yearOptions,
                                    currentValue = year,
                                    onOptionSelected = { year = it }
                                )
                            }
                    ) {
                        OutlinedTextField(
                            value = year,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Year") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Year") },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_year_dropdown")
                        )
                    }

                    // Court Name (System Dropdown)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerSystemOptionPicker(
                                    context = context,
                                    title = "Select Court Name",
                                    options = courtOptions,
                                    currentValue = courtReferredFrom,
                                    onOptionSelected = { courtReferredFrom = it }
                                )
                            }
                    ) {
                        OutlinedTextField(
                            value = courtReferredFrom,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Court Name") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Court") },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_court_dropdown")
                        )
                    }

                    // Informant Name
                    OutlinedTextField(
                        value = petitioner,
                        onValueChange = { petitioner = it },
                        label = { Text("Informant Name") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
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
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
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

                    // Mediator Name (System Dropdown)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerSystemOptionPicker(
                                    context = context,
                                    title = "Select Mediator Name",
                                    options = mediatorOptions,
                                    currentValue = mediator,
                                    onOptionSelected = { mediator = it }
                                )
                            }
                    ) {
                        OutlinedTextField(
                            value = mediator,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Mediator Name") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Mediator") },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field_mediator_dropdown")
                        )
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
    onOpenMoreOptions: () -> Unit = {},
    tasks: List<TaskItem> = emptyList(),
    onAddTask: (String) -> Unit = {},
    onToggleTask: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. CASE HEADER CARD (Top rectangular area with press-and-hold for more options)
        CaseRecordBentoCard(
            case = case,
            isSelected = false,
            isSelectionMode = false,
            onToggleSelected = {},
            onClick = {}, // Clicking top card inside details doesn't close it, it's a header
            onEditClick = onEdit,
            onDeleteClick = onDelete,
            isDetailView = true,
            onStatusChange = onStatusChange,
            onNextDateChange = onNextDateChange,
            onLongClick = onOpenMoreOptions
        )

        // 2. CASE DETAILS SECTION (Court & Mediator Side-by-Side with small font to fit)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "CASE DETAILS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                color = PrimaryBlue
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerColor, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardColor),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Court (Left column)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Court / Jurisdiction",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = case.courtReferredFrom.ifEmpty { "N/A" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .width(1.dp)
                            .background(DividerColor)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Mediator (Right column)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Mediator",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = case.mediator.ifEmpty { "N/A" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. PARTY DETAILS SECTION (Side by side name & number, click to dial directly)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "PARTY DETAILS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                color = PrimaryBlue
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DividerColor, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardColor),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Party 1: Petitioner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (case.petitionerPhone.isNotBlank() && case.petitionerPhone != "N/A") {
                                    try {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${case.petitionerPhone}"))
                                        context.startActivity(dialIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot dial ${case.petitionerPhone}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "No phone number available for petitioner", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Petitioner / Informant",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = case.petitioner.ifEmpty { "N/A" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = if (case.petitionerPhone.isNotBlank() && case.petitionerPhone != "N/A") case.petitionerPhone else "No phone",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (case.petitionerPhone.isNotBlank() && case.petitionerPhone != "N/A") PrimaryBlue else TextMuted
                        )
                    }

                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 1.dp)

                    // Party 2: Respondent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (case.respondentPhone.isNotBlank() && case.respondentPhone != "N/A") {
                                    try {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${case.respondentPhone}"))
                                        context.startActivity(dialIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot dial ${case.respondentPhone}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "No phone number available for respondent", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Respondent / Defendant",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = case.respondent.ifEmpty { "N/A" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = if (case.respondentPhone.isNotBlank() && case.respondentPhone != "N/A") case.respondentPhone else "No phone",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (case.respondentPhone.isNotBlank() && case.respondentPhone != "N/A") WarningOrange else TextMuted
                        )
                    }
                }
            }
        }

        // 4. NOTES & TASKS SECTION
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NOTES & TASKS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = PrimaryBlue
                )
                if (tasks.isNotEmpty()) {
                    Text(
                        text = "${tasks.count { !it.isCompleted }} pending",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = WarningOrange
                    )
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = {
                    Text(
                        text = "e.g., Advocate requested to inform parties, conduct mediation and submit report...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = "Notes Icon",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCardColor,
                    unfocusedContainerColor = SurfaceCardColor,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = DividerColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
            )

            // Update / Create Task button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(PrimaryBlue, Color(0xFF4361EE))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (noteText.isBlank()) {
                            Toast.makeText(context, "Please enter a note before updating", Toast.LENGTH_SHORT).show()
                        } else {
                            onAddTask(noteText.trim())
                            noteText = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddTask,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Update Note & Create Task",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White
                    )
                }
            }

            // Display case tasks list if any
            if (tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tasks.forEach { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, DividerColor, RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { onToggleTask(task.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SuccessGreen,
                                        uncheckedColor = TextSecondary
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.note,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                        ),
                                        color = if (task.isCompleted) TextMuted else Color.White
                                    )
                                    Text(
                                        text = task.date,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
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

// Dialog helper triggering native Android System Single Choice Selection Dialog
fun triggerSystemOptionPicker(
    context: Context,
    title: String,
    options: List<String>,
    currentValue: String,
    onOptionSelected: (String) -> Unit
) {
    val selectedIndex = options.indexOf(currentValue).coerceAtLeast(0)
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setSingleChoiceItems(options.toTypedArray(), selectedIndex) { dialog, which ->
            onOptionSelected(options[which])
            dialog.dismiss()
        }
        .setNegativeButton("Cancel", null)
        .show()
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

