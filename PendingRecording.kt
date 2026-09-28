package com.mtracker

data class PendingRecording(
    val id: Long = 0,
    val filePath: String,
    val status: Status = Status.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isTranscribed: Boolean = false,
    val transcribedText: String? = null,
    val aiResultJson: String? = null
) {
    enum class Status {
        PENDING, PROCESSING, REVIEWING, COMPLETED, FAILED
    }

    fun shouldRetry(): Boolean = retryCount < 10 && status == Status.PENDING
    fun isPermanentlyFailed(): Boolean = status == Status.FAILED || (retryCount >= 10 && status != Status.COMPLETED)
}
