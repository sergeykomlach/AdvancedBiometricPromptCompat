package dev.skomlach.common.device

import com.google.gson.annotations.SerializedName


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