package com.example.myledger.data

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.repository.LedgerRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class LedgerDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository
    private val clock = MutableClock(Instant.parse("2026-10-03T12:00:00Z"))
    private val businessDate = LocalDate.of(2026, 9, 30)
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedCategories).build()
        repository = LedgerRepository(database, clock)
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    private fun expense(amount: Long = 1234, date: LocalDate = businessDate) = TransactionEntity(
        type = TransactionType.EXPENSE, amountMinor = amount, categoryId = 1, date = date,
    )

    @Test fun defaultCategoriesExistBeforeFirstReadAndInitializationIsIdempotent() = runBlocking {
        assertEquals(DefaultCategories.all, database.categoryDao().getAll())
        repeat(3) { repository.initialize() }
        assertEquals(17, database.categoryDao().getAll().size)
        assertEquals(
            listOf("餐饮", "交通", "住宿", "日用", "购物", "娱乐", "学习科研", "医疗健康", "通讯订阅", "人情社交", "其他"),
            repository.observeCategories(TransactionType.EXPENSE).firstValue().map { it.name },
        )
        assertEquals(
            listOf("固定工资", "项目酬金", "实验室事务", "奖学金/奖励", "其他收入"),
            repository.observeCategories(TransactionType.INCOME).firstValue().map { it.name },
        )
        assertEquals(listOf("报销"), repository.observeCategories(TransactionType.REIMBURSEMENT).firstValue().map { it.name })
    }

    @Test fun transactionDaoInsertUpdateDeleteRoundTripsEveryField() = runBlocking {
        val activity = database.activityDao().insert(ActivityEntity(name = "ISCA 2027", type = ActivityType.WORK))
        val input = expense().copy(activityId = activity, reimbursable = true, note = "午饭", createdAt = 100, updatedAt = 200)
        val id = database.transactionDao().insert(input)
        assertEquals(input.copy(id = id), database.transactionDao().getById(id))
        val updated = input.copy(id = id, amountMinor = 1899, date = businessDate.minusDays(1), note = "晚饭", updatedAt = 300)
        assertEquals(1, database.transactionDao().update(updated))
        assertEquals(updated, database.transactionDao().getById(id))
        assertEquals(1, database.transactionDao().deleteById(id))
        assertNull(database.transactionDao().getById(id))
        assertEquals(0, database.transactionDao().deleteById(id))
    }

    @Test fun amountAboveDoublePrecisionLimitRemainsExact() = runBlocking {
        val amount = 9_007_199_254_740_993L
        val id = repository.addTransaction(expense(amount))
        assertEquals(amount, repository.getTransaction(id)!!.amountMinor)
    }

    @Test fun repositorySeparatesBusinessDateFromCreationAndPreservesCreationOnEdit() = runBlocking {
        val id = repository.addTransaction(expense().copy(createdAt = 1, updatedAt = 2))
        val initial = repository.getTransaction(id)!!
        assertEquals(businessDate, initial.date)
        assertEquals(clock.millis(), initial.createdAt)
        assertEquals(initial.createdAt, initial.updatedAt)
        clock.current = clock.current.plusSeconds(60)
        repository.updateTransaction(initial.copy(date = businessDate.minusDays(1), amountMinor = 999, createdAt = 0))
        val updated = repository.getTransaction(id)!!
        assertEquals(initial.createdAt, updated.createdAt)
        assertEquals(clock.millis(), updated.updatedAt)
        assertEquals(businessDate.minusDays(1), updated.date)
        assertTrue(repository.deleteTransaction(id))
        assertFalse(repository.deleteTransaction(id))
    }

    @Test fun dateRangeIsInclusiveAndSortedByBusinessDateThenId() = runBlocking {
        val start = LocalDate.of(2026, 9, 30)
        val end = LocalDate.of(2026, 10, 1)
        val ids = repository.addTransactions(listOf(
            expense(date = start.minusDays(1)), expense(date = end), expense(date = start),
            expense(date = end), expense(date = end.plusDays(1)),
        ))
        assertEquals(listOf(ids[3], ids[1], ids[2]), database.transactionDao().getBetween(start, end).map { it.id })
        assertEquals(listOf(ids[3], ids[1], ids[2]), repository.observeTransactions(start, end).firstValue().map { it.id })
        assertEquals(listOf(ids[2]), repository.getTransactions(start, start).map { it.id })
        assertTrue(repository.getTransactions(start.minusYears(1), end.minusYears(1)).isEmpty())
        fails<IllegalArgumentException> { repository.getTransactions(end, start) }
    }

    @Test fun flowEmitsInsertUpdateAndDeleteWithoutResubscribing() = runBlocking {
        val emissions = Channel<List<TransactionEntity>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) {
            repository.observeTransactions().collect { emissions.send(it) }
        }
        suspend fun awaitState(predicate: (List<TransactionEntity>) -> Boolean): List<TransactionEntity> = withTimeout(10_000) {
            var rows = emissions.receive()
            while (!predicate(rows)) rows = emissions.receive()
            rows
        }
        try {
            assertTrue(awaitState { it.isEmpty() }.isEmpty())
            val id = repository.addTransaction(expense())
            val inserted = awaitState { it.singleOrNull()?.id == id }.single()
            repository.updateTransaction(inserted.copy(amountMinor = 2222))
            assertEquals(2222L, awaitState { it.singleOrNull()?.amountMinor == 2222L }.single().amountMinor)
            repository.deleteTransaction(id)
            assertTrue(awaitState { it.isEmpty() }.isEmpty())
        } finally {
            collector.cancel()
            collector.join()
            emissions.close()
        }
    }

    @Test fun daoBatchInsertRollsBackAllRowsOnForeignKeyFailure() = runBlocking {
        repository.initialize()
        fails<SQLiteConstraintException> {
            database.transactionDao().insertAll(listOf(expense(), expense().copy(categoryId = 999)))
        }
        assertTrue(repository.observeTransactions().firstValue().isEmpty())
    }

    @Test fun repositoryBatchSavesTenIndependentRowsAndRejectsInvalidBatch() = runBlocking {
        val ids = repository.addTransactions((1L..10L).map { expense(it) })
        assertEquals(10, ids.distinct().size)
        assertEquals((1L..10L).toList(), repository.observeTransactions().firstValue().sortedBy { it.id }.map { it.amountMinor })
        fails<IllegalArgumentException> { repository.addTransactions(listOf(expense(), expense(0))) }
        assertEquals(10, repository.observeTransactions().firstValue().size)
    }

    @Test fun incomeAndReimbursementRetainDistinctTypesAndCategories() = runBlocking {
        val ids = repository.addTransactions(listOf(
            expense().copy(type = TransactionType.INCOME, categoryId = 12),
            expense().copy(type = TransactionType.REIMBURSEMENT, categoryId = 16),
        ))
        assertEquals(TransactionType.INCOME, repository.getTransaction(ids[0])!!.type)
        assertEquals(TransactionType.REIMBURSEMENT, repository.getTransaction(ids[1])!!.type)
    }

    @Test fun repositoryRejectsInvalidAmountCategoryAndActivityWithoutWriting() = runBlocking {
        for (input in listOf(
            expense(0), expense(-1), expense().copy(categoryId = 999),
            expense().copy(categoryId = 12), expense().copy(activityId = 999), expense().copy(id = 100),
        )) fails<IllegalArgumentException> { repository.addTransaction(input) }
        fails<IllegalArgumentException> { repository.updateTransaction(expense().copy(id = 999)) }
        assertTrue(repository.observeTransactions().firstValue().isEmpty())
    }

    @Test fun activityDaoRoundTripsOptionalDatesTypesAndNotes() = runBlocking {
        val input = ActivityEntity(name = "上海演唱会", type = ActivityType.PERSONAL, note = "门票")
        val id = database.activityDao().insert(input)
        assertEquals(input.copy(id = id), database.activityDao().getById(id))
        val updated = input.copy(id = id, name = "项目出差", type = ActivityType.WORK, startDate = businessDate, endDate = businessDate.plusDays(2))
        assertEquals(1, database.activityDao().update(updated))
        assertEquals(updated, database.activityDao().observeAll().firstValue().single())
        assertEquals(1, database.activityDao().deleteById(id))
        assertNull(database.activityDao().getById(id))
    }

    @Test fun deletingReferencedActivityFailsAndKeepsLedgerIntact() = runBlocking {
        val activity = repository.addActivity(ActivityEntity(name = "会议", type = ActivityType.WORK))
        val id = repository.addTransaction(expense().copy(activityId = activity, reimbursable = true))
        fails<SQLiteConstraintException> { repository.deleteActivity(activity) }
        assertNotNull(repository.getActivity(activity))
        assertEquals(activity, repository.getTransaction(id)!!.activityId)
        repository.updateTransaction(repository.getTransaction(id)!!.copy(activityId = null))
        assertTrue(repository.deleteActivity(activity))
        assertNotNull(repository.getTransaction(id))
    }

    @Test fun repositoryValidatesAndUpdatesActivity() = runBlocking {
        fails<IllegalArgumentException> { repository.addActivity(ActivityEntity(name = "  ", type = ActivityType.PERSONAL)) }
        fails<IllegalArgumentException> {
            repository.addActivity(ActivityEntity(name = "旅行", type = ActivityType.PERSONAL, startDate = businessDate, endDate = businessDate.minusDays(1)))
        }
        val id = repository.addActivity(ActivityEntity(name = "  旅行  ", type = ActivityType.PERSONAL))
        assertEquals("旅行", repository.getActivity(id)!!.name)
        repository.updateActivity(repository.getActivity(id)!!.copy(name = "出差", type = ActivityType.WORK))
        assertEquals(ActivityType.WORK, repository.observeActivities().firstValue().single().type)
        assertTrue(repository.deleteActivity(id))
        assertFalse(repository.deleteActivity(id))
    }

    @Test fun diskDatabaseReopensWithSameRecordsAndNoDuplicateCategories() = runBlocking {
        database.close()
        database = AppDatabase.create(context)
        repository = LedgerRepository(database, clock)
        val activity = repository.addActivity(ActivityEntity(name = "旅行", type = ActivityType.PERSONAL))
        val id = repository.addTransaction(expense().copy(activityId = activity, note = "保留"))
        val saved = repository.getTransaction(id)
        database.close()
        database = AppDatabase.create(context)
        repository = LedgerRepository(database, clock)
        repeat(3) { repository.initialize() }
        assertEquals(saved, repository.getTransaction(id))
        assertEquals("旅行", repository.getActivity(activity)!!.name)
        assertEquals(17, database.categoryDao().getAll().size)
    }

    private suspend inline fun <reified T : Throwable> fails(block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            assertTrue("Expected ${T::class.java.simpleName}, got $error", error is T)
            return
        }
        fail("Expected ${T::class.java.simpleName}")
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T =
        withTimeout(10_000) { this@firstValue.first() }

    private class MutableClock(var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }
}
