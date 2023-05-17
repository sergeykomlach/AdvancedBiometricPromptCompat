package dev.skomlach.common.device

import com.google.gson.annotations.SerializedName


data class Roms(

    @SerializedName("id") var id: String? = null,
    @SerializedName("photo") var photo: String? = null

)