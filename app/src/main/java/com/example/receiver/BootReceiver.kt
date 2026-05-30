package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val db = AppDatabase.getDatabase(context)
            val repository = ReminderRepository(db.reminderDao())
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val people = repository.getPeopleList()
                    for (person in people) {
                        if (person.isReminderEnabled && person.phoneNumber.isNotEmpty()) {
                            // Reschedule active background alarm securely on boot
                            val nextTrigger = ReminderScheduler.scheduleNextAlarm(context, person)
                            repository.savePerson(person.copy(nextReminderTimestamp = nextTrigger))
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
}
