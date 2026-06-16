// 文件: NetworkUtils.kt
package com.au.launcher.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object NetworkUtils {
    private const val IP_SERVICE_URL = "https://api.ipify.org?format=text"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 判断当前网络环境是否位于中国
     * 1. 获取设备的公网 IP
     * 2. 使用 ip2region 离线库查询 IP 归属地
     * 3. 如果归属地包含 "中国"，则返回 true
     */
    suspend fun isNetworkInChina(): Boolean = withContext(Dispatchers.IO) {
        // 1. 获取设备的公网 IP
        val publicIp = getPublicIpAddress()
        if (publicIp == null) {
            // 获取公网 IP 失败，回退到旧逻辑或直接返回 false
            return@withContext true
        }

        // 2. 使用之前准备好的离线库进行判断
        return@withContext try {
            IpRegionHelper.isChina(publicIp)
        } catch (e: Exception) {
            // 如果离线查询出错，也可以回退到旧逻辑，保证可用性
            true
        }
    }

    /**
     * 获取设备公网 IP
     */
    private suspend fun getPublicIpAddress(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(IP_SERVICE_URL).get().build()
        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}