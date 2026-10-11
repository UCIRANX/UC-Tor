package ir.uciran.uctor

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/** Only apps a user can actually launch: user-installed apps plus launchable system apps. */
data class AppChoice(val packageName: String, val label: String, val icon: ApplicationInfo)

object AppChoices {
    fun list(ctx: Context): List<AppChoice> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != ctx.packageName && it.enabled }
            .distinctBy { it.packageName }
            .map { AppChoice(it.packageName, pm.getApplicationLabel(it).toString().trim(), it) }
            .filter { it.label.isNotBlank() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}
