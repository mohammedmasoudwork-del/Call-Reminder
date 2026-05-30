package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.CallRecord
import com.example.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val personId = intent.getIntExtra("PERSON_ID", -1)
        if (personId == -1) return

        val db = AppDatabase.getDatabase(context)
        val repository = ReminderRepository(db.reminderDao())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val person = repository.getPersonById(personId) ?: return@launch

                if (action == "com.example.ACTION_SHOW_REMINDER") {
                    if (person.isReminderEnabled && person.phoneNumber.isNotEmpty()) {
                        ReminderScheduler.showReminderNotification(context, person)
                    }
                } else if (action == "com.example.ACTION_CHECK_CALL_LOG") {
                    val contactName = person.name
                    val targetPhone = person.phoneNumber

                    if (targetPhone.isEmpty()) return@launch

                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_CALL_LOG
                    ) == PackageManager.PERMISSION_GRANTED

                    var callVerified = false
                    var verifiedDuration = 0

                    if (hasPermission) {
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val todayStart = calendar.timeInMillis

                        // Check outgoing calls placed today
                        val uri = CallLog.Calls.CONTENT_URI
                        val projection = arrayOf(
                            CallLog.Calls.NUMBER,
                            CallLog.Calls.TYPE,
                            CallLog.Calls.DATE,
                            CallLog.Calls.DURATION
                        )
                        val selection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?"
                        val selectionArgs = arrayOf(
                            CallLog.Calls.OUTGOING_TYPE.toString(),
                            todayStart.toString()
                        )

                        val cursor: Cursor? = context.contentResolver.query(
                            uri, projection, selection, selectionArgs, "${CallLog.Calls.DATE} DESC"
                        )

                        cursor?.use {
                            val numCol = it.getColumnIndex(CallLog.Calls.NUMBER)
                            val durCol = it.getColumnIndex(CallLog.Calls.DURATION)

                            if (numCol >= 0 && durCol >= 0) {
                                while (it.moveToNext()) {
                                    val number = it.getString(numCol) ?: ""
                                    val duration = it.getInt(durCol) // in seconds
                                    if (PhoneNumberUtils.compare(context, number, targetPhone)) {
                                        if (duration >= person.minCallDurationSeconds) {
                                            callVerified = true
                                            verifiedDuration = duration
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (callVerified) {
                        val now = System.currentTimeMillis()
                        // Save a successful CallRecord
                        repository.addCallRecord(
                            CallRecord(
                                personId = person.id,
                                personName = contactName,
                                timestamp = now,
                                commType = "CALL",
                                durationSeconds = verifiedDuration,
                                isManual = false
                            )
                        )

                        // Reschedule next formal reminder from now
                        val updatedPerson = person.copy(
                            lastContactTimestamp = now
                        )
                        val nextTrigger = ReminderScheduler.scheduleNextAlarm(context, updatedPerson)
                        repository.savePerson(updatedPerson.copy(nextReminderTimestamp = nextTrigger))

                        // Show success confirmation notification
                        ReminderScheduler.showCallConfirmedNotification(context, contactName, person.id)
                    } else {
                        // Verification failed. Notify user and snooze the alarm
                        ReminderScheduler.showCallFailedNotification(context, contactName, person.id)
                        val snoozeAt = ReminderScheduler.scheduleSnoozeAlarm(context, person)
                        repository.savePerson(person.copy(nextReminderTimestamp = snoozeAt))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
