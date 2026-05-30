package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CallRecord
import com.example.data.Person
import com.example.data.ReminderRepository
import com.example.receiver.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = ReminderRepository(db.reminderDao())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val peopleList: StateFlow<List<Person>> = repository.peopleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredPeopleList: StateFlow<List<Person>> = combine(peopleList, _searchQuery) { list, query ->
        if (query.trim().isEmpty()) {
            list
        } else {
            list.filter { person ->
                person.name.contains(query, ignoreCase = true) || person.phoneNumber.contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callHistory: StateFlow<List<CallRecord>> = repository.allCallRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _pendingWhatsAppConfirm = MutableStateFlow<Person?>(null)
    val pendingWhatsAppConfirm: StateFlow<Person?> = _pendingWhatsAppConfirm

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent

    init {
        // Prepopulate with a few beautiful custom Arabic seed relations to assist the user out-of-the-box!
        viewModelScope.launch {
            val existing = repository.getPeopleList()
            if (existing.isEmpty()) {
                val seed1 = Person(
                    name = "أمي الغالية",
                    phoneNumber = "01123456789",
                    photoUri = "avatar_1", // Use unique avatar identifiers
                    preferredCommType = "CALL",
                    intervalDays = 2,
                    snoozeMinutes = 30,
                    minCallDurationSeconds = 60,
                    notes = "أغلى روح بالكون. الاتصال بها يوم بعد يوم لمباركة الحياة ❤️"
                )
                val seed2 = Person(
                    name = "أختي الحبيبة",
                    phoneNumber = "01234567890",
                    photoUri = "avatar_2",
                    preferredCommType = "BOTH",
                    intervalDays = 7,
                    snoozeMinutes = 60,
                    minCallDurationSeconds = 45,
                    defaultWhatsAppMessage = "السلام عليكم يا أختي العظيمة، طمنيني عنك وكيف أحوالك؟ 🌸",
                    notes = "مكالمة أسبوعية للاطمئنان على البيت وأخبار العائلة."
                )
                val seed3 = Person(
                    name = "صديقي محمد",
                    phoneNumber = "01012345678",
                    photoUri = "avatar_3",
                    preferredCommType = "WHATSAPP",
                    intervalDays = 14,
                    snoozeMinutes = 120,
                    minCallDurationSeconds = 30,
                    defaultWhatsAppMessage = "يا صديقي العزيز! صار لنا تواصل مفقود من فترة، طمني وعساك طيب وبأحسن حال 👍✨",
                    notes = "صديق الدراسة القديم، نرسل له رسالة ودية ونستطلع أحواله."
                )

                val id1 = repository.savePerson(seed1)
                val id2 = repository.savePerson(seed2)
                val id3 = repository.savePerson(seed3)

                // Reschedule for these seed values
                ReminderScheduler.scheduleNextAlarm(getApplication(), seed1.copy(id = id1.toInt()))
                ReminderScheduler.scheduleNextAlarm(getApplication(), seed2.copy(id = id2.toInt()))
                ReminderScheduler.scheduleNextAlarm(getApplication(), seed3.copy(id = id3.toInt()))
            }
            checkPendingWhatsAppConfirm()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun checkPendingWhatsAppConfirm() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("call_reminder_prefs", Context.MODE_PRIVATE)
        val pendingId = sharedPrefs.getInt("PENDING_WHATSAPP_PERSON_ID", -1)
        if (pendingId != -1) {
            viewModelScope.launch {
                val person = repository.getPersonById(pendingId)
                if (person != null) {
                    _pendingWhatsAppConfirm.value = person
                } else {
                    // Stale data, cleanup
                    sharedPrefs.edit().remove("PENDING_WHATSAPP_PERSON_ID").apply()
                }
            }
        }
    }

    fun confirmWhatsAppContact(person: Person, success: Boolean) {
        viewModelScope.launch {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("call_reminder_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().remove("PENDING_WHATSAPP_PERSON_ID").apply()
            _pendingWhatsAppConfirm.value = null

            if (success) {
                val now = System.currentTimeMillis()
                // 1. Log manual contact
                repository.addCallRecord(
                    CallRecord(
                        personId = person.id,
                        personName = person.name,
                        timestamp = now,
                        commType = "WHATSAPP",
                        durationSeconds = 0,
                        isManual = true
                    )
                )

                // 2. Reschedule standard reminder
                val updatedPerson = person.copy(
                    lastContactTimestamp = now
                )
                val nextTrigger = ReminderScheduler.scheduleNextAlarm(getApplication(), updatedPerson)
                repository.savePerson(updatedPerson.copy(nextReminderTimestamp = nextTrigger))

                _toastEvent.emit("whatsapp_confirmed")
            } else {
                // Snooze and wait
                val snoozeAt = ReminderScheduler.scheduleSnoozeAlarm(getApplication(), person)
                repository.savePerson(person.copy(nextReminderTimestamp = snoozeAt))
                _toastEvent.emit("whatsapp_snoozed")
            }
        }
    }

    fun savePerson(
        id: Int,
        name: String,
        phoneNumber: String,
        photoUri: String,
        preferredCommType: String,
        intervalDays: Int,
        snoozeMinutes: Int,
        minCallDurationSeconds: Int,
        defaultWhatsAppMessage: String,
        notes: String,
        isReminderEnabled: Boolean
    ) {
        viewModelScope.launch {
            val existing = if (id > 0) repository.getPersonById(id) else null
            val personToSave = Person(
                id = if (id > 0) id else 0,
                name = name,
                phoneNumber = phoneNumber,
                photoUri = photoUri,
                preferredCommType = preferredCommType,
                intervalDays = intervalDays,
                snoozeMinutes = snoozeMinutes,
                minCallDurationSeconds = minCallDurationSeconds,
                defaultWhatsAppMessage = defaultWhatsAppMessage,
                notes = notes,
                isReminderEnabled = isReminderEnabled,
                lastContactTimestamp = existing?.lastContactTimestamp ?: 0L,
                nextReminderTimestamp = existing?.nextReminderTimestamp ?: 0L
            )

            val savedId = repository.savePerson(personToSave)
            val updatedPersonWithId = personToSave.copy(id = if (id > 0) id else savedId.toInt())

            if (isReminderEnabled && phoneNumber.isNotEmpty()) {
                val nextTrigger = ReminderScheduler.scheduleNextAlarm(getApplication(), updatedPersonWithId)
                repository.savePerson(updatedPersonWithId.copy(nextReminderTimestamp = nextTrigger))
            } else {
                ReminderScheduler.cancelAlarm(getApplication(), updatedPersonWithId)
            }

            _toastEvent.emit("person_saved")
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            ReminderScheduler.cancelAlarm(getApplication(), person)
            repository.clearHistoryForPerson(person.id)
            repository.deletePerson(person)
            _toastEvent.emit("person_deleted")
        }
    }

    fun toggleReminder(person: Person, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && person.phoneNumber.trim().isEmpty()) {
                _toastEvent.emit("phone_number_required")
                return@launch
            }
            val updated = person.copy(isReminderEnabled = enabled)
            if (enabled) {
                val nextTrigger = ReminderScheduler.scheduleNextAlarm(getApplication(), updated)
                repository.savePerson(updated.copy(nextReminderTimestamp = nextTrigger))
                _toastEvent.emit("reminder_enabled")
            } else {
                ReminderScheduler.cancelAlarm(getApplication(), person)
                repository.savePerson(updated.copy(nextReminderTimestamp = 0L))
                _toastEvent.emit("reminder_disabled")
            }
        }
    }

    fun triggerWhatsApp(context: Context, person: Person) {
        val phoneNumber = person.phoneNumber
        val cleanPhone = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
        if (cleanPhone.isNotEmpty()) {
            val whatsappUri = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(person.defaultWhatsAppMessage)}"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(whatsappUri)).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Save pending verification trigger
            val sharedPrefs = getApplication<Application>().getSharedPreferences("call_reminder_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putInt("PENDING_WHATSAPP_PERSON_ID", person.id).apply()
            _pendingWhatsAppConfirm.value = person

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Clear state since WhatsApp wasn't triggered
                sharedPrefs.edit().remove("PENDING_WHATSAPP_PERSON_ID").apply()
                _pendingWhatsAppConfirm.value = null
                viewModelScope.launch {
                    _toastEvent.emit("whatsapp_not_installed")
                }
            }
        }
    }

    fun triggerCall(context: Context, person: Person) {
        val phoneNumber = person.phoneNumber
        if (phoneNumber.isNotEmpty()) {
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            // Register background logger
            ReminderScheduler.scheduleCallLogCheck(context, person)
        }
    }

    fun triggerManualConfirm(person: Person) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val isWhatsapp = (person.preferredCommType == "WHATSAPP")
            repository.addCallRecord(
                CallRecord(
                    personId = person.id,
                    personName = person.name,
                    timestamp = now,
                    commType = if (isWhatsapp) "WHATSAPP" else "CALL",
                    durationSeconds = 0,
                    isManual = true
                )
            )

            val updated = person.copy(lastContactTimestamp = now)
            val nextAlarm = ReminderScheduler.scheduleNextAlarm(getApplication(), updated)
            repository.savePerson(updated.copy(nextReminderTimestamp = nextAlarm))

            _toastEvent.emit("call_confirmed_manual")
        }
    }

    fun triggerTestNotification(person: Person) {
        ReminderScheduler.showReminderNotification(getApplication(), person)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _toastEvent.emit("history_cleared")
        }
    }
}
