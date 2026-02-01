package com.monday8am.koogagent.data.huggingface

/** DTOs for Hugging Face API responses. */

/**
 * Represents a model from the Hugging Face API list endpoint. GET
 * https://huggingface.co/api/models?author=litert-community
 */
data class HuggingFaceModelSummary(
    val id: String,
    val pipelineTag: String?,
    val gated: GatedStatus,
    val downloads: Int,
    val likes: Int,
)

/** Gated status can be boolean or string "auto". */
sealed class GatedStatus {
    /** Model is not gated - no authentication required */
    data object None : GatedStatus()

    /** Model requires manual approval */
    data object Manual : GatedStatus()

    /** Model uses automatic gating (license acceptance required) */
    data object Auto : GatedStatus()

    /** Whether this model requires any form of authentication */
    val isGated: Boolean
        get() = this != None

    companion object {
        /**
         * Parses the gated field from Hugging Face API response. The field can be: false, true, or
         * "auto"
         */
        fun fromApiValue(value: Any?): GatedStatus =
            when (value) {
                false,
                "false" -> None

                true,
                "true" -> Manual

                "auto" -> Auto
                else -> None
            }
    }
}
