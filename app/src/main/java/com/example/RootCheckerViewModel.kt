package com.example

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CheckStatus {
    NOT_RUN,
    SECURE,       // Safe status
    SUSPICIOUS,   // Minor indicators but not definite root
    ROOT_FOUND    // Definite root or modified privileges found
}

enum class OverallDeviceStatus {
    UNSCANNED,
    SECURE,
    SUSPICIOUS,
    ROOTED
}

data class CheckItem(
    val id: String,
    val titleRu: String,
    val titleEn: String,
    val descRu: String,
    val descEn: String,
    val status: CheckStatus = CheckStatus.NOT_RUN,
    val details: String = ""
)

data class RootCheckerState(
    val isScanning: Boolean = false,
    val overallStatus: OverallDeviceStatus = OverallDeviceStatus.UNSCANNED,
    val checks: List<CheckItem> = emptyList(),
    val logs: List<String> = emptyList(),
    val activeSuGranted: Boolean? = null,
    val isRequestingActiveSu: Boolean = false,
    // Dynamic settings state
    val language: String = "ru", // "ru" or "en"
    val isDarkTheme: Boolean = false,
    val isOnboardingCompleted: Boolean = false
)

class RootCheckerViewModel : ViewModel() {

    private val _state = MutableStateFlow(RootCheckerState(checks = getInitialChecks()))
    val state = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var sharedPreferences: android.content.SharedPreferences? = null

    private fun getInitialChecks(): List<CheckItem> {
        return listOf(
            CheckItem(
                id = "build_tags",
                titleRu = "Метки сборки (Build Tags)",
                titleEn = "Build Tags Status",
                descRu = "Проверка наличия метки 'test-keys' от разработчиков сторонних прошивок.",
                descEn = "Checks if firmware is compiled with 'test-keys' tags usually from custom developer ROMs."
            ),
            CheckItem(
                id = "su_paths",
                titleRu = "Поиск бинарника 'su'",
                titleEn = "su Binary Search",
                descRu = "Поиск исполняемого файла управления суперпользователем по стандартным путям.",
                descEn = "Looks for the Superuser binary along typical system paths."
            ),
            CheckItem(
                id = "busybox",
                titleRu = "Поиск утилиты BusyBox",
                titleEn = "BusyBox Detection",
                descRu = "Поиск пакета утилит unix-команд, часто поставляемого с root-проверенными девайсами.",
                descEn = "Searches for standard Unix helper tools often associated with development."
            ),
            CheckItem(
                id = "su_exec",
                titleRu = "Тест запуска 'su' в PATH",
                titleEn = "su CLI Execution Test",
                descRu = "Проверка присутствия утилиты 'su' в системном PATH с помощью команды default shell.",
                descEn = "Executes command line resolution query on 'su' utility via runtime command tools."
            ),
            CheckItem(
                id = "root_apps",
                titleRu = "Менеджеры и модули (Magisk, KSU, APatch, LSPosed/Vector)",
                titleEn = "Managers & Modules (Magisk, KSU, APatch, LSPosed/Vector)",
                descRu = "Поиск установленного ПО суперпользователя и управления фреймворками.",
                descEn = "Scans for installed superuser managers and framework controllers."
            ),
            CheckItem(
                id = "sys_write",
                titleRu = "Тест записи в системный раздел",
                titleEn = "System Directory Write Test",
                descRu = "Попытка создания тестового файла в защищенной директории /system.",
                descEn = "Attempts to create a mock file inside system directories."
            ),
            CheckItem(
                id = "selinux",
                titleRu = "Анализ статуса SELinux",
                titleEn = "SELinux Status Audit",
                descRu = "Проверка, активен ли режим принудительной безопасности (Enforcing/Permissive).",
                descEn = "Verifies whether Security-Enhanced Linux enforces safety policies."
            )
        )
    }

    fun initSettings(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences("root_checker_settings", Context.MODE_PRIVATE)
            val lang = sharedPreferences?.getString("language", "ru") ?: "ru"
            val dark = sharedPreferences?.getBoolean("dark_theme", false) ?: false
            val onboardingCompleted = sharedPreferences?.getBoolean("onboarding_completed", false) ?: false
            _state.value = _state.value.copy(
                language = lang,
                isDarkTheme = dark,
                isOnboardingCompleted = onboardingCompleted
            )
        }
    }

    fun toggleLanguage(context: Context) {
        val nextLang = if (_state.value.language == "ru") "en" else "ru"
        _state.value = _state.value.copy(language = nextLang)
        sharedPreferences?.edit()?.putString("language", nextLang)?.apply()
        if (_state.value.overallStatus != OverallDeviceStatus.UNSCANNED && !_state.value.isScanning) {
            startSystemScan(context)
        }
    }

    fun setLanguage(context: Context, lang: String) {
        _state.value = _state.value.copy(language = lang)
        sharedPreferences?.edit()?.putString("language", lang)?.apply()
        if (_state.value.overallStatus != OverallDeviceStatus.UNSCANNED && !_state.value.isScanning) {
            startSystemScan(context)
        }
    }

    fun toggleTheme() {
        val nextDark = !_state.value.isDarkTheme
        _state.value = _state.value.copy(isDarkTheme = nextDark)
        sharedPreferences?.edit()?.putBoolean("dark_theme", nextDark)?.apply()
    }

    fun setTheme(dark: Boolean) {
        _state.value = _state.value.copy(isDarkTheme = dark)
        sharedPreferences?.edit()?.putBoolean("dark_theme", dark)?.apply()
    }

    fun completeOnboarding() {
        _state.value = _state.value.copy(isOnboardingCompleted = true)
        sharedPreferences?.edit()?.putBoolean("onboarding_completed", true)?.apply()
    }

    private fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        _state.value = _state.value.copy(
            logs = _state.value.logs + "[$timestamp] $message"
        )
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList(), activeSuGranted = null)
    }

    fun startSystemScan(context: Context) {
        if (_state.value.isScanning) return

        val isEn = _state.value.language == "en"

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isScanning = true,
                overallStatus = OverallDeviceStatus.UNSCANNED,
                checks = getInitialChecks(),
                logs = emptyList(),
                activeSuGranted = null
            )

            val modelInfo = "Manufacturer: ${Build.MANUFACTURER}, Model: ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
            if (isEn) {
                addLog("=== SYSTEM SAFETY SCAN INITIATED ===")
                addLog(modelInfo)
                addLog("Auditing system elements for root indicators (SU/Privilege escalation)...")
            } else {
                addLog("=== ЗАПУСК ПОЛНОЙ ДИАГНОСТИКИ СИСТЕМЫ ===")
                addLog("Устройство: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                addLog("Поиск признаков привилегий суперпользователя (SU/Root)...")
            }
            delay(400)

            val updatedChecks = _state.value.checks.toMutableList()

            // 1. Build Tags Check
            if (isEn) addLog("[1/7] Inspecting Build.TAGS...") else addLog("[1/7] Проверка Build.TAGS...")
            val hasTestKeys = withContext(Dispatchers.IO) { RootDetector.checkBuildTags() }
            val status1 = if (hasTestKeys) CheckStatus.SUSPICIOUS else CheckStatus.SECURE
            val detail1 = if (hasTestKeys) {
                if (isEn) "Found tag: '${Build.TAGS}' (indicates unofficial custom firmware)" 
                else "Найден tag: '${Build.TAGS}' (указывает на неофициальную кастомную прошивку)"
            } else {
                if (isEn) "Official launch image tags: '${Build.TAGS}'" 
                else "Официальная заводская метка сборки: '${Build.TAGS}'"
            }
            updatedChecks[0] = updatedChecks[0].copy(status = status1, details = detail1)
            addLog(
                if (hasTestKeys) {
                    if (isEn) "⚠️ Warning: Custom test-keys build tags detected" else "⚠️ Предупреждение: Обнаружены test-keys в прошивке"
                } else {
                    if (isEn) "✔️ Standard release-keys signature matched" else "✔️ Метки сборки соответствуют стандартным (release-keys)"
                }
            )
            _state.value = _state.value.copy(checks = updatedChecks.toList())
            delay(300)

            // 2. SU Binary Path Check
            if (isEn) addLog("[2/7] Searching system paths for 'su' files...") else addLog("[2/7] Поиск бинарных файлов 'su' по стандартным путям...")
            val suPaths = withContext(Dispatchers.IO) { RootDetector.checkSuBinaryPaths() }
            val status2 = if (suPaths.isNotEmpty()) CheckStatus.ROOT_FOUND else CheckStatus.SECURE
            val detail2 = if (suPaths.isNotEmpty()) {
                if (isEn) "su executable discovered at:\n" + suPaths.joinToString("\n")
                else "Найден файл 'su' в: \n" + suPaths.joinToString("\n")
            } else {
                if (isEn) "No superuser binaries found along configured system paths"
                else "Файлы superuser не найдены в системных директориях"
            }
            updatedChecks[1] = updatedChecks[1].copy(status = status2, details = detail2)
            addLog(
                if (suPaths.isNotEmpty()) {
                    if (isEn) "❌ THREAT DETECTED: su binary found in directories!" else "❌ ОБНАРУЖЕН: Бинарный файл su присутствует в системе!"
                } else {
                    if (isEn) "✔️ No su binary located on standard paths" else "✔️ Бинарник su по стандартным путям не обнаружен"
                }
            )
            _state.value = _state.value.copy(checks = updatedChecks.toList())
            delay(300)

            // 3. BusyBox Detection
            if (isEn) addLog("[3/7] Scanning system environment for BusyBox utility...") else addLog("[3/7] Проверка системного окружения на наличие BusyBox...")
            val bbPaths = withContext(Dispatchers.IO) { RootDetector.checkBusyBoxPaths() }
            val status3 = if (bbPaths.isNotEmpty()) CheckStatus.SUSPICIOUS else CheckStatus.SECURE
            val detail3 = if (bbPaths.isNotEmpty()) {
                if (isEn) "BusyBox toolkit found at:\n" + bbPaths.joinToString("\n")
                else "Инструменты BusyBox найдены: \n" + bbPaths.joinToString("\n")
            } else {
                if (isEn) "BusyBox toolkit not found in standard system paths"
                else "BusyBox не найден в системных путях"
            }
            updatedChecks[2] = updatedChecks[2].copy(status = status3, details = detail3)
            addLog(
                if (bbPaths.isNotEmpty()) {
                    if (isEn) "⚠️ Warning: BusyBox tools present" else "⚠️ Предупреждение: busybox обнаружен в системе"
                } else {
                    if (isEn) "✔️ BusyBox toolkit is absent" else "✔️ Вспомогательные пакеты BusyBox не обнаружены"
                }
            )
            _state.value = _state.value.copy(checks = updatedChecks.toList())
            delay(300)

            // 4. su Execution cli
            if (isEn) addLog("[4/7] Direct command line invocation test: 'which su'...") else addLog("[4/7] Тест выполнения 'which su'...")
            val canExecSu = withContext(Dispatchers.IO) { RootDetector.checkSuExecutionOnly() }
            val status4 = if (canExecSu) CheckStatus.ROOT_FOUND else CheckStatus.SECURE
            val detail4 = if (canExecSu) {
                if (isEn) "The 'su' command was resolved and successfully execution-tested."
                else "Утилита 'su' доступна и успешно отвечает в системном окружении PATH."
            } else {
                if (isEn) "Command execution request on 'su' returned failure (command not found)"
                else "Консольный вызов 'su' не распознается системой"
            }
            updatedChecks[3] = updatedChecks[3].copy(status = status4, details = detail4)
            addLog(
                if (canExecSu) {
                    if (isEn) "❌ THREAT DETECTED: 'su' call executes in shell context" else "❌ ОБНАРУЖЕН: CLI su успешно найден и отвечает"
                } else {
                    if (isEn) "✔️ Dynamic command su execution failed (not executable)" else "✔️ Поиск su утилиты через CLI завершился отказом (su не найден)"
                }
            )
            _state.value = _state.value.copy(checks = updatedChecks.toList())
            delay(300)

            // 5. Root Packages Scan
            if (isEn) addLog("[5/7] Scanning installed applications package names & metadata...") else addLog("[5/7] Сканирование пакетов установленных приложений...")
            val rootPkgs = withContext(Dispatchers.IO) { RootDetector.checkRootPackages(context).toMutableList() }
            
            // Query kernel level and mount level footprints for KernelSU
            val ksuIndicators = withContext(Dispatchers.IO) { RootDetector.checkKernelSuIndicators() }
            for (ind in ksuIndicators) {
                rootPkgs.add("KernelSU footprint: $ind")
                if (isEn) addLog("⚠️ Detected KernelSU indicator: $ind") else addLog("⚠️ Обнаружен след KernelSU: $ind")
            }

            // Query Magisk level traces
            val magiskIndicators = withContext(Dispatchers.IO) { RootDetector.checkMagiskIndicators() }
            for (ind in magiskIndicators) {
                rootPkgs.add("Magisk footprint: $ind")
                if (isEn) addLog("⚠️ Detected Magisk indicator: $ind") else addLog("⚠️ Обнаружен след Magisk: $ind")
            }

            // Query APatch level traces
            val apatchIndicators = withContext(Dispatchers.IO) { RootDetector.checkAPatchIndicators() }
            for (ind in apatchIndicators) {
                rootPkgs.add("APatch footprint: $ind")
                if (isEn) addLog("⚠️ Detected APatch indicator: $ind") else addLog("⚠️ Обнаружен след APatch: $ind")
            }

            // Query LSPosed/Xposed/Vector level traces
            val lsposedIndicators = withContext(Dispatchers.IO) { RootDetector.checkLSPosedIndicators() }
            for (ind in lsposedIndicators) {
                rootPkgs.add("LSPosed/Vector footprint: $ind")
                if (isEn) addLog("⚠️ Detected LSPosed/Vector indicator: $ind") else addLog("⚠️ Обнаружен след LSPosed/Vector: $ind")
            }

            val status5 = if (rootPkgs.isNotEmpty()) CheckStatus.ROOT_FOUND else CheckStatus.SECURE
            val detail5 = if (rootPkgs.isNotEmpty()) {
                if (isEn) "Detected managers, modules, or hidden framework structures:\n" + rootPkgs.joinToString("\n")
                else "Найдены известные менеджеры Root, модули или скрытые структуры фреймворков:\n" + rootPkgs.joinToString("\n")
            } else {
                if (isEn) "No known rooting package components (Magisk, APatch, KernelSU, LSPosed, Vector) identified"
                else "Установленные менеджеры или контроллеры (Magisk/KernelSU/APatch/LSPosed/Vector) не обнаружены"
            }
            updatedChecks[4] = updatedChecks[4].copy(status = status5, details = detail5)
            addLog(
                if (rootPkgs.isNotEmpty()) {
                    if (isEn) "❌ THREAT DETECTED: Root apps, active modules, or kernel footprints identified!" else "❌ ОБНАРУЖЕН: Найдены установленные root-приложения, активные модули или следы в ядре!"
                } else {
                    if (isEn) "✔️ Checked manager application signatures and hidden traces: safe" else "✔️ Известных root-приложений, системных модулей и скрытых следов нет"
                }
            )
            _state.value = _state.value.copy(checks = updatedChecks.toList())
            delay(300)

            // 6. Write to System Directories
            if (isEn) addLog("[6/7] Probing write permissions on read-only system partitions...") else addLog("[6/7] Тестирование прав записи на защищенные разделы...")
            val canWrite = withContext(Dispatchers.IO) { RootDetector.checkSystemWritable() }
            val status6 = if (canWrite) CheckStatus.ROOT_FOUND else CheckStatus.SECURE
            val detail6 = if (canWrite) {
                if (isEn) "Successfully wrote temporary files inside system directory! Safety exploit: System partition is read-write."
                else "Успешно записан тестовый файл в защищенном разделе /system! Опасность: Системный раздел смонтирован как Read-Write."
            } else {
                if (isEn) "Read-only access verified. Partition creation request in /system rejected by OS (Access Denied)."
                else "Доступ только на чтение. Попытка записи в /system отклонена системой (Отказ в доступе/Read-only file system)."
            }
            updatedChecks[5] = updatedChecks[5].copy(status = status6, details = detail6)
            addLog(
                if (canWrite) {
                    if (isEn) "❌ THREAT DETECTED: System partition opened as writable!" else "❌ ОБНАРУЖЕН: Системный раздел открыт для записи!"
                } else {
                    if (isEn) "✔️ Partition integrity verified (correctly read-only)" else "✔️ Системные разделы корректно защищены от записи"
                }
            )
            _state.value = _state.value.copy(checks = updatedChecks.toList())
            delay(300)

            // 7. SELinux Check
            if (isEn) addLog("[7/7] Getting SELinux core runtime policy status...") else addLog("[7/7] Проверка режима контроля безопасности SELinux...")
            val enforceStatus = withContext(Dispatchers.IO) { RootDetector.checkSELinuxStatus() }
            val isPermissive = enforceStatus.trim().equals("Permissive", ignoreCase = true)
            val status7 = if (isPermissive) CheckStatus.SUSPICIOUS else CheckStatus.SECURE
            val detail7 = if (isEn) "SELinux runtime policy outcome: $enforceStatus" else "Режим работы SELinux на устройстве: $enforceStatus"
            updatedChecks[6] = updatedChecks[6].copy(status = status7, details = detail7)
            addLog(
                if (isEn) "Info: SELinux working mode detected as: $enforceStatus" else "Информация: SELinux находится в режиме: $enforceStatus"
            )
            _state.value = _state.value.copy(checks = updatedChecks.toList())
            delay(400)

            // Determine Overall State
            val hasRoot = updatedChecks.any { it.status == CheckStatus.ROOT_FOUND }
            val hasWarning = updatedChecks.any { it.status == CheckStatus.SUSPICIOUS }

            val finalOverallStatus = when {
                hasRoot -> OverallDeviceStatus.ROOTED
                hasWarning -> OverallDeviceStatus.SUSPICIOUS
                else -> OverallDeviceStatus.SECURE
            }

            if (isEn) {
                addLog("=== SAFETY SCAN COMPLETED ===")
                addLog("Final Diagnosis: " + when (finalOverallStatus) {
                    OverallDeviceStatus.ROOTED -> "DEVICE IS ROOTED! Security compromises found."
                    OverallDeviceStatus.SUSPICIOUS -> "SUSPICIOUS PARAMETERS OBSERVED! (Possible cloaked/partially enabled root)."
                    OverallDeviceStatus.SECURE -> "DEVICE IS SECURE. No active root factors discovered."
                    else -> ""
                })
            } else {
                addLog("=== ДИАГНОСТИКА ЗАВЕРШЕНА ===")
                addLog("Вердикт системы: " + when (finalOverallStatus) {
                    OverallDeviceStatus.ROOTED -> "УСТРОЙСТВО ИМЕЕТ ROOT-ДОСТУП! Безопасность под угрозой."
                    OverallDeviceStatus.SUSPICIOUS -> "ОБНАРУЖЕНЫ ПОДОЗРИТЕЛЬНЫЕ МЕТРИКИ! (Возможен скрытый или модифицированный Root)."
                    OverallDeviceStatus.SECURE -> "УСТРОЙСТВО БЕЗОПАСНО. Root-доступ не обнаружен."
                    else -> ""
                })
            }

            _state.value = _state.value.copy(
                isScanning = false,
                overallStatus = finalOverallStatus
            )
        }
    }

    fun requestInteractiveSu() {
        if (_state.value.isRequestingActiveSu) return

        val isEn = _state.value.language == "en"

        viewModelScope.launch {
            _state.value = _state.value.copy(isRequestingActiveSu = true)
            if (isEn) {
                addLog("=== INTERACTIVE SU SHELL ESCALATION REQUEST ===")
                addLog("Dispatching runtime system request on 'su' shell...")
            } else {
                addLog("=== ИНТЕРАКТИВНЫЙ ЗАПРОС ROOT ПРАВ ===")
                addLog("Запуск системного вызова команды 'su'...")
            }
            delay(100)

            val success = withContext(Dispatchers.IO) { RootDetector.checkInteractiveSuAccess() }
            if (isEn) {
                addLog("Resolution of 'su': " + if (success) {
                    "✔️ Granted! Root shell session successfully configured (Process return 0)."
                } else {
                    "❌ Blocked! Shell invocation either rejected by developer panel/Superuser or execution denied."
                })
            } else {
                addLog("Результат выполнения 'su': " + if (success) {
                    "✔️ Доступ УСПЕШНО получен! Shell сессия ответила кодом завершения 0."
                } else {
                    "❌ Доступ ОТКЛОНЕН! Команда su завершилась ошибкой или была запрещена менеджером root/системой."
                })
            }

            _state.value = _state.value.copy(
                isRequestingActiveSu = false,
                activeSuGranted = success
            )
        }
    }

    fun getLocalizedText(key: String): String {
        return getTranslation(key, _state.value.language)
    }

    private fun getTranslation(key: String, language: String): String {
        val enMap = mapOf(
            "app_title" to "Root Checker",
            "device_secure" to "Device is Secure",
            "device_suspicious" to "Suspicious State",
            "device_rooted" to "Device is Rooted",
            "device_unscanned" to "Scan Required",
            "all_tests_passed" to "All tests completed",
            "overall_status_sub_secure" to "SU binaries and other traces not found. Your device is safe.",
            "overall_status_sub_suspicious" to "Some parameters are suspicious. Hidden superuser is possible.",
            "overall_status_sub_rooted" to "Serious root access indicators detected.",
            "overall_status_sub_unscanned" to "Run scan for a comprehensive safety audit of your system.",
            "su_granted_banner_title" to "Interactive root granted! Your application WAS ABLE to call 'su' shell and switch to superuser.",
            "su_denied_banner_title" to "Check completed. Interactive su request was denied by user or blocked by safety system.",
            "start_test_btn" to "Start Scan",
            "scanning_btn" to "Scanning...",
            "test_su_call_btn" to "Test su Call",
            "terminal_title" to "Scanning Process Logs",
            "terminal_placeholder" to "No logs available. Click the button above to start diagnostics...",
            "edu_header" to "What are Root Rights?",
            "edu_desc" to "A root account or Superuser is a hidden system account in Android OS that grants unrestricted super-administrator privileges to modify source files and system structures.",
            "edu_risks_header" to "Key Risks of having Root:",
            "edu_bullet_1" to "Banking apps might be blocked/disabled to safeguard against fraud.",
            "edu_bullet_2" to "Malicious software can silently intercept personal files, passwords, and messages.",
            "edu_bullet_3" to "Accidental deletions of partition files can lead to boot loops or a hard brick.",
            "settings_btn" to "Settings",
            "select_theme" to "App Theme",
            "dark_theme" to "Dark Theme",
            "light_theme" to "Light Theme",
            "select_lang" to "Interface Language",
            "lang_ru" to "Russian (RU)",
            "lang_en" to "English (EN)",
            "close_btn" to "Close",
            "welcome" to "Welcome!",
            "onboarding_desc" to "This app will analyze your phone's safety status for root privileges, managers, and system exploits.",
            "onboarding_config_label" to "Choose initial settings:",
            "get_started" to "Get Started",
            "tech_label" to "ENGLISH VERSION OF TEST:",
            "details_label" to "DETAILS AND RESULTS:",
            "not_run_details" to "Test has not been run yet. Please launch diagnostic scanning..."
        )

        val ruMap = mapOf(
            "app_title" to "Проверка Root",
            "device_secure" to "Устройство безопасно",
            "device_suspicious" to "Подозрительное состояние",
            "device_rooted" to "Устройство рутировано",
            "device_unscanned" to "Требуется проверка",
            "all_tests_passed" to "Тестирование пройдено",
            "overall_status_sub_secure" to "Бинарные файлы SU и другие следы не найдены. Устройство в безопасности.",
            "overall_status_sub_suspicious" to "Некоторые параметры вызывают подозрения. Возможен скрытый суперпользователь.",
            "overall_status_sub_rooted" to "Обнаружены серьезные индикаторы root-прав.",
            "overall_status_sub_unscanned" to "Запустите сканирование для полной проверки безопасности вашей системы.",
            "su_granted_banner_title" to "Интерактивный доступ получен! Ваше приложение СМОГЛО вызвать su-оболочку и переключиться в суперпользователя.",
            "su_denied_banner_title" to "Проверка завершена. Интерактивный вызов su был отклонен пользователем или блокирован системой безопасности.",
            "start_test_btn" to "Начать тест",
            "scanning_btn" to "Секунду...",
            "test_su_call_btn" to "Тест Вызова su",
            "terminal_title" to "Логи процессов сканирования",
            "terminal_placeholder" to "Логи отсутствуют. Нажмите кнопку выше для запуска диагностики системы...",
            "edu_header" to "Что такое Root-права?",
            "edu_desc" to "Root-аккаунт или Суперпользователь — это скрытая учетная запись в ОС Android, дающая неограниченные права супер-администратора на внесение изменений в исходный код и файловую систему.",
            "edu_risks_header" to "Основные риски наличия Root:",
            "edu_bullet_1" to "Банковские программы (Сбер, Альфа и др.) могут блокироваться с целью защиты от мошенников.",
            "edu_bullet_2" to "Вредоносный софт может получить скрытый доступ ко всем вашим личным файлам, паролям и СМС.",
            "edu_bullet_3" to "Неосторожные изменения системных файлов могут привести к полной неисправности устройства (кирпичу).",
            "settings_btn" to "Настройки",
            "select_theme" to "Тема оформления",
            "dark_theme" to "Темная тема",
            "light_theme" to "Светлая тема",
            "select_lang" to "Язык интерфейса",
            "lang_ru" to "Русский (RU)",
            "lang_en" to "Английский (EN)",
            "close_btn" to "Закрыть",
            "welcome" to "Добро пожаловать!",
            "onboarding_desc" to "Приложение проанализирует безопасность вашего телефона на наличие Root прав, установленных менеджеров и уязвимостей.",
            "onboarding_config_label" to "Выберите начальные настройки:",
            "get_started" to "Начать работу",
            "tech_label" to "АНГЛИЙСКАЯ ВЕРСИЯ ТЕСТА:",
            "details_label" to "ДЕТАЛИ И РЕЗУЛЬТАТЫ:",
            "not_run_details" to "Тест еще не запускался. Пожалуйста, запустите диагностическое сканирование..."
        )

        return if (language == "en") enMap[key] ?: key else ruMap[key] ?: key
    }
}
