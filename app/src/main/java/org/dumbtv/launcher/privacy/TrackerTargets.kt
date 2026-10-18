package org.dumbtv.launcher.privacy

/** Named recommendation/ACR packages; core Google Play services are deliberately excluded. */
object TrackerTargets {
    data class App(val packageName: String, val title: String)
    val all = listOf(
        App("tv.samba.ssm", "Samba TV"),
        App("com.sony.dtv.sonyselect", "Sony Select"),
        App("com.google.android.apps.tv.launcherx", "Google TV Home"),
        App("com.google.android.tvlauncher", "Android TV Home"),
        App("com.google.android.tvrecommendations", "Google TV recommendations"),
    )
    fun selectInstalled(packages: Set<String>) = all.filter { it.packageName in packages }

    fun safeToBlock(uid: Int, packagesSharingUid: List<String>): Boolean =
        uid >= 10000 && packagesSharingUid.isNotEmpty() &&
            packagesSharingUid.all { name -> all.any { it.packageName == name } }

}
