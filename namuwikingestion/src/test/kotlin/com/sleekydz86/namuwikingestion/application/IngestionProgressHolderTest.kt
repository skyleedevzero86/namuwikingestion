package com.sleekydz86.namuwikingestion.application

import com.sleekydz86.namuwikingestion.global.enums.IngestionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IngestionProgressHolderTest {

    private val holder = IngestionProgressHolder()

    @Test
    fun `get - 초기 상태는 IDLE`() {
        val state = holder.get()
        assertEquals(IngestionStatus.IDLE, state.status)
        assertEquals(0, state.rowsRead)
        assertEquals(0, state.inserted)
        assertNull(state.startedAt)
        assertNull(state.finishedAt)
        assertNull(state.errorMessage)
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `start - RUNNING으로 전이되고 startedAt 설정`() {
        holder.start()
        val state = holder.get()
        assertEquals(IngestionStatus.RUNNING, state.status)
        assertEquals(0, state.rowsRead)
        assertEquals(0, state.inserted)
        assertNotNull(state.startedAt)
        assertNull(state.finishedAt)
    }

    @Test
    fun `update - rowsRead와 inserted 반영 및 history에 스냅샷 추가`() {
        holder.start()
        holder.update(100, 50)
        val state = holder.get()
        assertEquals(100, state.rowsRead)
        assertEquals(50, state.inserted)
        assertEquals(1, state.history.size)
        assertEquals(100, state.history.first().rowsRead)
        assertEquals(50, state.history.first().inserted)
    }

    @Test
    fun `done - DONE 상태로 전이, finishedAt 설정`() {
        holder.start()
        holder.update(200, 200)
        holder.done(200, 200)
        val state = holder.get()
        assertEquals(IngestionStatus.DONE, state.status)
        assertEquals(200, state.rowsRead)
        assertEquals(200, state.inserted)
        assertNotNull(state.finishedAt)
        assertTrue(state.history.size >= 2)
    }

    @Test
    fun `error - ERROR 상태로 전이, errorMessage 설정`() {
        holder.start()
        holder.error("테스트 에러")
        val state = holder.get()
        assertEquals(IngestionStatus.ERROR, state.status)
        assertEquals("테스트 에러", state.errorMessage)
    }

    @Test
    fun `reset - IDLE로 초기화`() {
        holder.start()
        holder.update(10, 5)
        holder.reset()
        val state = holder.get()
        assertEquals(IngestionStatus.IDLE, state.status)
        assertEquals(0, state.rowsRead)
        assertEquals(0, state.inserted)
        assertNull(state.startedAt)
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `update 여러 번 - history가 maxHistorySize 이하로 유지`() {
        holder.start()
        repeat(150) { i ->
            holder.update(i, i)
        }
        val state = holder.get()
        assertEquals(149, state.rowsRead)
        assertTrue(state.history.size <= 120, "history는 최대 120개로 제한")
    }
}
