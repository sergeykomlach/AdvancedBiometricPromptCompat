/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */



package org.ifaa.android.manager

import android.content.Context
import android.os.Build.VERSION

abstract class IFAAManager {
    /**
     * 返回手机系统上支持的校验方式，目前IFAF协议1.0版本指纹为0x01、虹膜为0x02
     */
    abstract fun getSupportBIOTypes(context: Context?): Int

    /**
     * 启动系统的指纹/虹膜管理应用界面，让用户进行指纹录入。指纹录入是在系统的指纹管理应用中实现的，
     * 本函数的作用只是将指纹管理应用运行起来，直接进行页面跳转，方便用户录入。
     * @param context
     * @param authType 生物特征识别类型，指纹为1，虹膜为2
     * @return 0，成功启动指纹管理应用；-1，启动指纹管理应用失败。
     */
    abstract fun startBIOManager(context: Context?, authType: Int): Int

    /**
     * 通过ifaateeclient的so文件实现REE到TA的通道
     * @param context
     * @param param 用于传输到IFAA TA的数据buffer
     * @return IFAA TA返回给REE数据buffer
     */
    external fun processCmd(context: Context?, param: ByteArray?): ByteArray?

    /**
     * 获取设备型号，同一款机型型号需要保持一致
     */
    abstract val deviceModel: String?

    /**
     * 获取IFAAManager接口定义版本，目前为1
     */
    abstract val version: Int

    companion object {
        private const val IFAA_VERSION_V2 = 2
        private const val IFAA_VERSION_V3 = 3
        private const val IFAA_VERSION_V4 = 4
        var sIfaaVer = 0
        var sIsFod = false

        /**
         * load so to communicate from REE to TEE
         */
        init {
            sIfaaVer = 1
            if (VERSION.SDK_INT >= 28) {
                sIfaaVer = IFAA_VERSION_V4
            } else if (sIsFod) {
                sIfaaVer = IFAA_VERSION_V3
            } else if (VERSION.SDK_INT >= 24) {
                sIfaaVer = IFAA_VERSION_V2
            } else {
                System.loadLibrary("teeclientjni") //teeclientjni for TA test binary //ifaateeclient
            }
        }
    }
}