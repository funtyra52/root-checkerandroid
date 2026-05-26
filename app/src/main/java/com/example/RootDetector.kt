package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.DataOutputStream

object RootDetector {

    // Common paths to search for "su" binary
    private val SU_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    )

    // Common paths to search for "busybox" binary
    private val BUSYBOX_PATHS = arrayOf(
        "/sbin/busybox",
        "/system/bin/busybox",
        "/system/xbin/busybox",
        "/data/local/xbin/busybox",
        "/data/local/bin/busybox",
        "/system/sd/xbin/busybox",
        "/system/bin/failsafe/busybox"
    )

    // Package names of known Superuser / Root/ Magisk management applications
    private val KNOWN_ROOT_APPS = arrayOf(
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.zachspong.temprootremovejb",
        "com.ramdroid.appquarantine",
        "com.topjohnwu.magisk"
    )

    /**
     * Check 1: Test Keys (Build Tags)
     * Returns true if build tags contain 'test-keys'
     */
    fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Check 2: Check standard directories for the 'su' binary.
     */
    fun checkSuBinaryPaths(): List<String> {
        val foundPaths = mutableListOf<String>()
        for (path in SU_PATHS) {
            val file = File(path)
            if (file.exists()) {
                foundPaths.add(path)
            }
        }
        return foundPaths
    }

    /**
     * Check 3: Check standard paths for the 'busybox' binary.
     */
    fun checkBusyBoxPaths(): List<String> {
        val foundPaths = mutableListOf<String>()
        for (path in BUSYBOX_PATHS) {
            val file = File(path)
            if (file.exists()) {
                foundPaths.add(path)
            }
        }
        return foundPaths
    }

    /**
     * Check 4: Check if we can execute "su" to request command shell.
     * Note: This does not block waiting for user permission, rather it tries to check if executable runs.
     */
    fun checkSuExecutionOnly(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            line != null
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    /**
     * Check 5: Active interactive test to trigger SU grant.
     * Returns true if we get positive shell permission.
     */
    fun checkInteractiveSuAccess(): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            false
        } finally {
            try { os?.close() } catch (e: Exception) {}
            process?.destroy()
        }
    }

    /**
     * Check 6: Check list of known root management packages and modules.
     */
    fun checkRootPackages(context: Context): List<String> {
        val foundPackages = mutableListOf<String>()
        val pm = context.packageManager

        // Comprehensive package and label registry for standard rooting apps and modules
        val registry = mapOf(
            "com.topjohnwu.magisk" to "Magisk Manager",
            "com.tiann.kernelsu" to "KernelSU Manager",
            "com.tiann.kernelsu.manager" to "KernelSU Manager",
            "me.weishu.kernelsu" to "KernelSU Manager (Alternative)",
            "me.bmax.apatch" to "APatch Manager",
            "org.lsposed.manager" to "LSPosed Manager",
            "org.lsposed.manager.vector" to "Vector Manager (LSPosed successor)",
            "me.weishu.lsposed" to "LSPosed Manager (Alternative)",
            "com.noshufou.android.su" to "Superuser",
            "eu.chainfire.supersu" to "SuperSU"
        )

        // 1. Direct targeted queries (bypasses QUERY_ALL_PACKAGES restriction if defined in <queries>)
        for ((packageName, label) in registry) {
            try {
                pm.getPackageInfo(packageName, 0)
                foundPackages.add("$label ($packageName)")
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 2. Full packages query as fallback/enrichment if permitted
        try {
            val installed = pm.getInstalledPackages(0)
            for (pkgInfo in installed) {
                val pkgName = pkgInfo.packageName ?: continue
                val lowerPkg = pkgName.lowercase()

                // Skip if already found via direct query
                if (registry.containsKey(pkgName) && foundPackages.any { it.contains(pkgName) }) {
                    continue
                }

                if (lowerPkg.contains("kernelsu") || lowerPkg.contains("apatch") || lowerPkg.contains("lsposed") || lowerPkg.contains("magisk")) {
                    val appLabel = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkgName
                    foundPackages.add("$appLabel ($pkgName)")
                } else if (lowerPkg.contains("vector") && (lowerPkg.contains("manager") || lowerPkg.contains("xposed") || lowerPkg.contains("lsposed") || lowerPkg.contains("root"))) {
                    val appLabel = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: "Vector Manager"
                    foundPackages.add("$appLabel ($pkgName)")
                }
            }
        } catch (e: Exception) {
            // Might lack QUERY_ALL_PACKAGES or sandbox restriction is active
        }

        return foundPackages.distinct()
    }

    /**
     * Check 7: Check if standard system directory writing can be achieved.
     * Generally, /system is mounted read-only.
     */
    fun checkSystemWritable(): Boolean {
        return try {
            val file = File("/system/test_root_access.txt")
            if (file.createNewFile()) {
                file.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            // Usually throws Permission Denied exception, which is normal on non-root devices.
            false
        }
    }

    /**
     * Dedicated check for KernelSU (KSU) indicators.
     * KernelSU operates at the kernel level and can hide packages, but leaves footprints in mounts and /sys filesystem.
     */
    fun checkKernelSuIndicators(): List<String> {
        val indicators = mutableListOf<String>()

        // 1. Check directory /sys/module/kernelsu
        val sysModuleKsu = File("/sys/module/kernelsu")
        if (sysModuleKsu.exists() && sysModuleKsu.isDirectory) {
            indicators.add("KernelSU Module (/sys/module/kernelsu)")
        }

        // 2. Check directory /sys/kernel/kernelsu
        val sysKernelKsu = File("/sys/kernel/kernelsu")
        if (sysKernelKsu.exists() && sysKernelKsu.isDirectory) {
            indicators.add("KernelSU Driver (/sys/kernel/kernelsu)")
        }

        // 3. Scan /proc/self/mounts and /proc/mounts
        val mountPaths = arrayOf("/proc/self/mounts", "/proc/mounts")
        for (path in mountPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    file.forEachLine { line ->
                        val lower = line.lowercase()
                        if (lower.contains("kernelsu") || (lower.contains("overlay") && lower.contains("ksu")) || lower.contains("/adb/ksu")) {
                            val msg = "KernelSU mount in $path"
                            if (!indicators.contains(msg)) {
                                indicators.add(msg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return indicators
    }

    /**
     * Dedicated check for Magisk indicators.
     * Magisk leaves unique traces in processes, files, mounts, and directory trees.
     */
    fun checkMagiskIndicators(): List<String> {
        val indicators = mutableListOf<String>()

        // 1. Files & dirs (Note: some might be masked but checks are beneficial)
        val paths = arrayOf(
            "/sbin/.magisk",
            "/dev/.magisk",
            "/data/adb/magisk",
            "/init.magisk.rc",
            "/cache/magisk.log",
            "/data/adb/modules"
        )
        for (p in paths) {
            val f = File(p)
            if (f.exists()) {
                indicators.add("Magisk filepath: $p")
            }
        }

        // 2. Scan mount paths (very strong trace)
        val mountPaths = arrayOf("/proc/self/mounts", "/proc/mounts")
        for (path in mountPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    file.forEachLine { line ->
                        val lower = line.lowercase()
                        if (lower.contains("magisk") || lower.contains(".magisk/mirror") || lower.contains("/magisk/mirror") || (lower.contains("overlay") && lower.contains("magisk"))) {
                            val msg = "Magisk mount trace in $path"
                            if (!indicators.contains(msg)) {
                                indicators.add(msg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 3. Memory Maps check (Zygisk or Magisk library mappings in current process memory maps)
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                mapsFile.forEachLine { line ->
                    val lower = line.lowercase()
                    if (lower.contains("magisk") || lower.contains("zygiski") || lower.contains("zygisk")) {
                        val msg = "Zygisk/Magisk loaded in memory map"
                        if (!indicators.contains(msg)) {
                            indicators.add(msg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return indicators
    }

    /**
     * Dedicated check for APatch indicators.
     * APatch utilizes kernel patching and leaves characteristic footprints.
     */
    fun checkAPatchIndicators(): List<String> {
        val indicators = mutableListOf<String>()

        // 1. Sysfs module directories
        val paths = arrayOf(
            "/sys/module/apatch",
            "/sys/kernel/apatch",
            "/data/adb/ap",
            "/data/adb/apatch"
        )
        for (p in paths) {
            val f = File(p)
            if (f.exists()) {
                indicators.add("APatch filepath: $p")
            }
        }

        // 2. Scan mount points
        val mountPaths = arrayOf("/proc/self/mounts", "/proc/mounts")
        for (path in mountPaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    file.forEachLine { line ->
                        val lower = line.lowercase()
                        if (lower.contains("apatch") || lower.contains("/data/adb/ap/") || (lower.contains("overlay") && lower.contains("kp_"))) {
                            val msg = "APatch mount trace in $path"
                            if (!indicators.contains(msg)) {
                                indicators.add(msg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 3. Scan maps for apatch references (if any library/hook is injected)
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                mapsFile.forEachLine { line ->
                    val lower = line.lowercase()
                    if (lower.contains("apatch") || lower.contains("/data/adb/ap/")) {
                        val msg = "APatch mapped in memory"
                        if (!indicators.contains(msg)) {
                            indicators.add(msg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return indicators
    }

    /**
     * Dedicated check for LSPosed, Xposed, and Vector framework indicators.
     * These frameworks infect processes to hook methods and leave major footprints in maps and files.
     */
    fun checkLSPosedIndicators(): List<String> {
        val indicators = mutableListOf<String>()

        // 1. Well-known LSPosed/Xposed/Vector directories
        val paths = arrayOf(
            "/data/adb/lsposed",
            "/data/adb/modules/lsposed",
            "/data/adb/modules/riru_lsposed",
            "/data/adb/modules/zygisk_lsposed",
            "/data/adb/modules/vector"
        )
        for (p in paths) {
            val f = File(p)
            if (f.exists()) {
                indicators.add("LSPosed/Vector directory: $p")
            }
        }

        // 2. Scan memory maps (extremely effective since they inject their libraries & JARs into every process)
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                mapsFile.forEachLine { line ->
                    val lower = line.lowercase()
                    // Exclude standard vector graphics frameworks
                    if (lower.contains("libvectorgraphics") || lower.contains("vector_drawable")) {
                        return@forEachLine
                    }
                    val isLsposedOrXposed = lower.contains("lsposed") || lower.contains("libriru") || lower.contains("xposed") || lower.contains("edxposed")
                    val isRealVector = lower.contains("org.lsposed.manager.vector") || lower.contains("vector_lsposed") || lower.contains("/vector/lib/") || lower.contains("/vector/dex/")
                    if (isLsposedOrXposed || isRealVector) {
                        // Extract library/JAR name to make the indicator detail descriptive
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size > 5) {
                            val path = parts.last()
                            if (path.isNotEmpty() && path.startsWith("/")) {
                                val msg = "Active hook library/JAR mapped: ${path.substringAfterLast('/')}"
                                if (!indicators.contains(msg)) {
                                    indicators.add(msg)
                                }
                            }
                        }
                        if (indicators.isEmpty() || !indicators.any { it.contains("Memory map") || it.contains("library") }) {
                            val msg = "LSPosed/Xposed/Vector signatures in Memory maps"
                            if (!indicators.contains(msg)) {
                                indicators.add(msg)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Check for typical Classloader / runtime Xposed properties or standard variables in stack/traces
        try {
            val cls = Class.forName("de.robv.android.xposed.XposedBridge")
            indicators.add("XposedBridge class loaded in Runtime")
        } catch (e: Exception) {
            // Ignore
        }

        return indicators
    }

    /**
     * Check 8: Check if SELinux is set to Permissive.
     */
    fun checkSELinuxStatus(): String {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("getenforce")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            line ?: "Enforcing"
        } catch (e: Exception) {
            "Enforcing" // Default standard safe state assumption
        } finally {
            process?.destroy()
        }
    }
}
