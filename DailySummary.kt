package com.mtracker

data class DailySummary(
    val id: Long = 0,
    val date: String,
    val summaryText: String,
    val viewed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
