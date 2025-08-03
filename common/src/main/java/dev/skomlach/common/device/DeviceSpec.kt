package dev.skomlach.common.device

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class DeviceSpec(
    @SerializedName("brand") var brand: String? = null,
    @SerializedName("codename") var codename: String? = null,
    @SerializedName("name") var name: String? = null,
    @SerializedName("recoveries") var recoveries: ArrayList<Recoveries> = arrayListOf(),
    @SerializedName("roms") var roms: ArrayList<Roms> = arrayListOf(),
    @SerializedName("specs") var specs: Specs? = Specs()
)

@Keep
data class Specs(
    @SerializedName("cpu") var cpu: String? = null,
    @SerializedName("weight") var weight: String? = null,
    @SerializedName("year") var year: String? = null,
    @SerializedName("os") var os: String? = null,
    @SerializedName("chipset") var chipset: String? = null,
    @SerializedName("gpu") var gpu: String? = null,
    @SerializedName("sensors") var sensors: String? = null,
    @SerializedName("batlife") var batlife: String? = null,
    @SerializedName("internalmemory") var internalmemory: String? = null

)

@Keep
data class Recoveries(

    @SerializedName("id") var id: String? = null,
    @SerializedName("xdathread") var xdathread: String? = null,
    @SerializedName("maintainer") var maintainer: String? = null

)

@Keep
data class Roms(

    @SerializedName("id") var id: String? = null,
    @SerializedName("photo") var photo: String? = null

)

@Keep
data class Phone(
    @SerializedName("name") val name: String,
    @SerializedName("link") val link: String,
    @SerializedName("imgUrl") val imgUrl: String,
    @SerializedName("info") val info: Map<String, String>?,
    @SerializedName("brand") val brand: String
)
