package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaseViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("CacharDLSAPrefs", Context.MODE_PRIVATE)

    private val _directoryUri = MutableStateFlow<String?>(null)
    val directoryUri: StateFlow<String?> = _directoryUri.asStateFlow()

    private val _cases = MutableStateFlow<List<CaseRecord>>(emptyList())
    val cases: StateFlow<List<CaseRecord>> = _cases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    init {
        loadTasks()
        val savedUriStr = prefs.getString("selected_dir_uri", null)
        if (savedUriStr != null) {
            _directoryUri.value = savedUriStr
            loadRecords()
        }
    }

    fun loadTasks() {
        val jsonStr = prefs.getString("mediation_tasks_list", "") ?: ""
        _tasks.value = TaskItem.listFromJson(jsonStr)
    }

    fun addTask(case: CaseRecord?, note: String, onDone: () -> Unit = {}) {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        val dateStr = sdf.format(java.util.Date())
        val newTask = TaskItem(
            caseNumber = case?.caseNumber ?: "",
            caseYear = case?.year ?: "",
            caseTitle = if (case != null) "${case.petitioner} vs ${case.respondent}" else "",
            mediator = case?.mediator ?: "",
            note = note.trim(),
            date = dateStr,
            isCompleted = false
        )
        val updated = listOf(newTask) + _tasks.value
        _tasks.value = updated
        prefs.edit().putString("mediation_tasks_list", TaskItem.listToJson(updated)).apply()
        onDone()
    }

    fun toggleTask(taskId: String) {
        val updated = _tasks.value.map {
            if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
        }
        _tasks.value = updated
        prefs.edit().putString("mediation_tasks_list", TaskItem.listToJson(updated)).apply()
    }

    fun deleteTask(taskId: String) {
        val updated = _tasks.value.filter { it.id != taskId }
        _tasks.value = updated
        prefs.edit().putString("mediation_tasks_list", TaskItem.listToJson(updated)).apply()
    }

    fun setDirectoryUri(uri: Uri) {
        val uriStr = uri.toString()
        prefs.edit().putString("selected_dir_uri", uriStr).apply()
        _directoryUri.value = uriStr
        loadRecords()
    }

    fun clearDirectoryUri() {
        prefs.edit().remove("selected_dir_uri").apply()
        _directoryUri.value = null
        _cases.value = emptyList()
    }

    fun loadRecords() {
        val uriStr = _directoryUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val loadedList = withContext(Dispatchers.IO) {
                val list = mutableListOf<CaseRecord>()
                try {
                    val treeUri = Uri.parse(uriStr)
                    val docFile = DocumentFile.fromTreeUri(context, treeUri)
                    if (docFile != null && docFile.isDirectory) {
                        val files = docFile.listFiles()
                        for (file in files) {
                            if (file.isFile && file.name?.endsWith(".json") == true) {
                                try {
                                    context.contentResolver.openInputStream(file.uri)?.use { stream ->
                                        val jsonStr = stream.bufferedReader().use { it.readText() }
                                        val record = CaseRecordMapper.jsonToCase(jsonStr)
                                        list.add(record)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                list
            }
            _cases.value = loadedList.sortedBy { it.serialNumber }
            _isLoading.value = false
        }
    }

    fun getNextSerialNumber(): Int {
        val list = _cases.value
        return if (list.isEmpty()) {
            1
        } else {
            (list.maxOfOrNull { it.serialNumber } ?: 0) + 1
        }
    }

    fun saveCaseRecord(case: CaseRecord, onResult: (Boolean, String) -> Unit) {
        val uriStr = _directoryUri.value
        if (uriStr == null) {
            onResult(false, "No directory selected")
            return
        }

        // Serial number is generated by the app, so it is excluded from duplicate comparison.
        // Category, year and every user-entered field remain part of the comparison.
        val duplicate = _cases.value.firstOrNull { existing ->
            existing.serialNumber != case.serialNumber &&
                existing.copy(serialNumber = 0) == case.copy(serialNumber = 0)
        }
        if (duplicate != null) {
            onResult(false, "This exact case record already exists")
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val treeUri = Uri.parse(uriStr)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: return@withContext Pair(false, "Unable to access the selected folder")
                    val files = docDir.listFiles().filter { it.isFile && it.name?.endsWith(".json") == true }

                    // Reuse the existing file when editing a record; this also supports older
                    // files whose names did not include category or serial number.
                    val existingFile = files.firstOrNull { file ->
                        try {
                            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                                val old = CaseRecordMapper.jsonToCase(stream.bufferedReader().use { it.readText() })
                                old.serialNumber == case.serialNumber &&
                                    old.caseNumber == case.caseNumber &&
                                    old.year == case.year &&
                                    old.category.equals(case.category, ignoreCase = true)
                            } ?: false
                        } catch (_: Exception) {
                            false
                        }
                    }

                    val safeCategory = case.category.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val safeCaseNumber = case.caseNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val fileName = "${safeCategory}_${safeCaseNumber}_${case.year}_${case.serialNumber}.json"
                    val targetFile = existingFile ?: docDir.createFile("application/json", fileName)
                        ?: return@withContext Pair(false, "Unable to create the case file")

                    context.contentResolver.openOutputStream(targetFile.uri, "rwt")?.use { out ->
                        out.write(CaseRecordMapper.caseToJson(case).toByteArray(Charsets.UTF_8))
                    } ?: return@withContext Pair(false, "Unable to write the case file")

                    Pair(true, "Case record saved successfully")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Pair(false, "Failed to save case record: ${e.localizedMessage ?: "Unknown error"}")
                }
            }

            if (result.first) loadRecords()
            onResult(result.first, result.second)
        }
    }

    fun deleteCaseRecord(case: CaseRecord, onResult: (Boolean) -> Unit) {
        val uriStr = _directoryUri.value ?: run {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val treeUri = Uri.parse(uriStr)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: return@withContext false
                    val matchingFile = docDir.listFiles().firstOrNull { file ->
                        if (!file.isFile || file.name?.endsWith(".json") != true) return@firstOrNull false
                        try {
                            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                                val stored = CaseRecordMapper.jsonToCase(stream.bufferedReader().use { it.readText() })
                                stored.serialNumber == case.serialNumber &&
                                    stored.caseNumber == case.caseNumber &&
                                    stored.year == case.year &&
                                    stored.category.equals(case.category, ignoreCase = true)
                            } ?: false
                        } catch (_: Exception) {
                            false
                        }
                    }
                    matchingFile?.delete() ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            if (success) loadRecords()
            onResult(success)
        }
    }

    fun purgeAllRecords(onResult: (Boolean, String) -> Unit) {
        val uriStr = _directoryUri.value
        if (uriStr == null) {
            onResult(false, "No directory selected")
            return
        }
        viewModelScope.launch {
            val deletedCount = withContext(Dispatchers.IO) {
                var count = 0
                try {
                    val treeUri = Uri.parse(uriStr)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                    if (docDir != null && docDir.isDirectory) {
                        val files = docDir.listFiles()
                        for (file in files) {
                            if (file.isFile && file.name?.endsWith(".json") == true) {
                                if (file.delete()) {
                                    count++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                count
            }
            loadRecords()
            onResult(true, "Successfully purged $deletedCount case files")
        }
    }
}
