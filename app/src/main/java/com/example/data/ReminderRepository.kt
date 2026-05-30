package com.example.data

import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val reminderDao: ReminderDao) {

    val peopleFlow: Flow<List<Person>> = reminderDao.getAllPeopleFlow()

    suspend fun getPeopleList(): List<Person> = reminderDao.getAllPeopleList()

    suspend fun getPersonById(id: Int): Person? = reminderDao.getPersonById(id)

    suspend fun savePerson(person: Person): Long {
        return reminderDao.insertPerson(person)
    }

    suspend fun deletePerson(person: Person) {
        reminderDao.deletePerson(person)
    }

    val allCallRecords: Flow<List<CallRecord>> = reminderDao.getAllCallRecordsFlow()

    suspend fun addCallRecord(record: CallRecord) {
        reminderDao.insertCallRecord(record)
    }

    suspend fun clearHistory() {
        reminderDao.clearCallHistory()
    }

    suspend fun clearHistoryForPerson(personId: Int) {
        reminderDao.clearCallHistoryForPerson(personId)
    }
}
