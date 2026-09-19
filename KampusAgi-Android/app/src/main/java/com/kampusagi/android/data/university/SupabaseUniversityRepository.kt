package com.kampusagi.android.data.university

import com.kampusagi.android.data.common.mapErrors
import com.kampusagi.android.domain.university.University
import com.kampusagi.android.domain.university.UniversityRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

class SupabaseUniversityRepository @Inject constructor(
    private val client: SupabaseClient,
) : UniversityRepository {

    @Serializable
    private data class UniversityRow(
        val id: String,
        val name: String,
        @SerialName("short_name") val shortName: String,
        val city: String,
    )

    override suspend fun getActiveUniversities(): List<University> = mapErrors {
        client.from("universities")
            .select(Columns.list("id", "name", "short_name", "city")) {
                order("name", Order.ASCENDING)
            }
            .decodeList<UniversityRow>()
            .map { University(id = it.id, name = it.name, shortName = it.shortName, city = it.city) }
    }
}
