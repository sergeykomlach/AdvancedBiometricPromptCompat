package dev.skomlach.common.device

import com.google.gson.annotations.SerializedName


data class Recoveries(

    @SerializedName("id") var id: String? = null,
    @SerializedName("xdathread") var xdathread: String? = null,
    @SerializedName("maintainer") var maintainer: String? = null

)