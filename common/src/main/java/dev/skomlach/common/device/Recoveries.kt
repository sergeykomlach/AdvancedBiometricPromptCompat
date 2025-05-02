package dev.skomlach.common.device

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Recoveries(

    @SerializedName("id") var id: String? = null,
    @SerializedName("xdathread") var xdathread: String? = null,
    @SerializedName("maintainer") var maintainer: String? = null

)