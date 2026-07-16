package com.streambert.tv.data.mdblist

import kotlinx.serialization.Serializable

/** Raw MDBList ratings response. */
@Serializable
data class MdbRatingsResponse(
    val title: String? = null,
    val ratings: List<MdbRatingDto> = emptyList()
)

@Serializable
data class MdbRatingDto(
    val source: String? = null,
    val value: Double? = null,
    val score: Int? = null,
    val votes: Int? = null
)

/** A rating ready for display on the detail page (label + formatted value). */
data class RatingBadge(
    val source: String,
    val label: String,
    val display: String
)
