package com.au.launcher.utils

import android.content.Context
import android.content.pm.PackageManager

object PackageUtils {
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getPackageNameFromId(id: String): String {
        return id.replace('_', '.')
    }

    fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        }
    }

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.filter { 
            (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 
        }.map { 
            AppInfo(
                name = it.loadLabel(pm).toString(),
                packageName = it.packageName
            )
        }.sortedBy { it.name }
    }
}

data class AppInfo(val name: String, val packageName: String)
