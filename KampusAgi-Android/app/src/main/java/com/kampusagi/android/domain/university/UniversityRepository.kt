package com.kampusagi.android.domain.university

data class University(
    val id: String,
    val name: String,
    val shortName: String,
    val city: String,
)

interface UniversityRepository {
    /** Yalnızca aktif üniversiteler (RLS zaten filtreler), ada göre sıralı. */
    suspend fun getActiveUniversities(): List<University>
}
