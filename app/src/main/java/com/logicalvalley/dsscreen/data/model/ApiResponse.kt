package com.logicalvalley.dsscreen.data.model

/**
 * Generic API response wrapper
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?,
    val error: String? = null
)

