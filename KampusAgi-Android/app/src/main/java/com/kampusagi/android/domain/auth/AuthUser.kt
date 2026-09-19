package com.kampusagi.android.domain.auth

data class AuthUser(
    val uid: String,
    val email: String?,
    val isEmailVerified: Boolean,
)
