package dev.skomlach.common.device

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Roms(

    @SerializedName("id") var id: String? = null,
    @SerializedName("photo") var photo: String? = null

)