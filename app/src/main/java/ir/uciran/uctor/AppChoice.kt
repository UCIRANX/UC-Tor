package ir.uciran.uctor

import android.content.Context
import android.content.pm.ApplicationInfo

data class AppChoice(val packageName: String, val label: String, val icon: ApplicationInfo)
object AppChoices {
    fun list(ctx: Context): List<AppChoice> = ctx.packageManager.getInstalledApplications(0)
        .filter { it.packageName != ctx.packageName && ctx.packageManager.getLaunchIntentForPackage(it.packageName) != null }
        .map { AppChoice(it.packageName, ctx.packageManager.getApplicationLabel(it).toString(), it) }
        .sortedBy { it.label.lowercase() }
}
