package dev.vic41148.somn.core.domain.repository

import dev.vic41148.somn.core.domain.model.Alarm

interface AlarmScheduler {
    fun schedule(alarm: Alarm)
    fun cancel(alarmId: Long)
}
