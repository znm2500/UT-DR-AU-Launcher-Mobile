package com.au.launcher.utils// IpRegionHelper.kt
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lionsoul.ip2region.xdb.Searcher
import java.io.File
import java.io.IOException
import com.au.launcher.R
object IpRegionHelper {
    private var searcher: Searcher? = null

    /**
     * 初始化，从 res/raw 复制数据库到缓存
     */
    suspend fun init(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (searcher != null) return@withContext true
        return@withContext try {
            // 1. 从 raw 资源复制到缓存文件
            val cacheFile = copyRawToCache(context, R.raw.ip2region, "ip2region.xdb")
                ?: return@withContext false

            // 2. 预加载向量索引（高并发）
            val vectorIndex = Searcher.loadVectorIndexFromFile(cacheFile.path)
            searcher = Searcher.newWithVectorIndex(cacheFile.path, vectorIndex)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从 raw 资源复制到缓存目录
     */
    private fun copyRawToCache(context: Context, rawResId: Int, fileName: String): File? {
        val cacheFile = File(context.cacheDir, fileName)
        if (cacheFile.exists()) {
            return cacheFile
        }
        return try {
            context.resources.openRawResource(rawResId).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 查询 IP 是否在中国
     */
    fun isChina(ipAddress: String): Boolean {
        val currentSearcher = searcher ?: return false
        return try {
            val region = currentSearcher.search(ipAddress)
            region.contains("中国")
        } catch (e: Exception) {
            false
        }
    }
}