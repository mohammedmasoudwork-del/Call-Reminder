package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.CallRecord
import com.example.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val personId = intent.getIntExtra("PERSON_ID", -1)
        if (personId == -1) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(personId) // Dismiss the alarm notification specifically

        val db = AppDatabase.getDatabase(context)
        val repository = ReminderRepository(db.reminderDao())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val person = repository.getPersonById(personId) ?: return@launch

                when (action) {
                    "com.example.ACTION_CALL_NOW" -> {
                        val phoneNumber = person.phoneNumber
                        if (phoneNumber.isNotEmpty()) {
                            // Open dialer safely
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(dialIntent)

                            // Schedule verification check in 90 seconds
                            ReminderScheduler.scheduleCallLogCheck(context, person)
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "لم يتم العثور على رقم هاتف متاح!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    "com.example.ACTION_SEND_WHATSAPP" -> {
                        val phoneNumber = person.phoneNumber
                        val messageText = person.defaultWhatsAppMessage
                        if (phoneNumber.isNotEmpty()) {
                            // Trim whitespace and special characters from phone number for Whatsapp formatting
                            var cleanPhone = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                            // Ensure there is some country prefix if needed (we'll log as is, or instruct users to type country suffix)
                            val whatsappUri = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(messageText)}"
                            val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUri)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }

                            // Store "Pending WhatsApp Confirmation" in Shared Preferences
                            val sharedPrefs = context.getSharedPreferences("call_reminder_prefs", Context.MODE_PRIVATE)
                            sharedPrefs.edit().putInt("PENDING_WHATSAPP_PERSON_ID", person.id).apply()

                            try {
                                context.startActivity(whatsappIntent)
                            } catch (e: Exception) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(context, "تطبيق واتساب غير مثبت على هذا الجهاز!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    "com.example.ACTION_SNOOZE" -> {
                        val snoozeAt = ReminderScheduler.scheduleSnoozeAlarm(context, person)
                        repository.savePerson(person.copy(nextReminderTimestamp = snoozeAt))

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "تم تأجيل تذكير ${person.name} لمدّة ${person.snoozeMinutes} دقيقة", Toast.LENGTH_SHORT).show()
                        }
                    }

                    "com.example.ACTION_CONFIRM_CALLED" -> {
                        val now = System.currentTimeMillis()
                        // 1. Log manual contact success
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

                        // 2. Reschedule standard reminder
                        val updatedPerson = person.copy(
                            lastContactTimestamp = now
                        )
                        val nextTrigger = ReminderScheduler.scheduleNextAlarm(context, updatedPerson)
                        repository.savePerson(updatedPerson.copy(nextReminderTimestamp = nextTrigger))

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "تمام، تم تأكيد وتوثيق التواصل مع ${person.name} ❤️", Toast.LENGTH_LONG).show()
                        }
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
