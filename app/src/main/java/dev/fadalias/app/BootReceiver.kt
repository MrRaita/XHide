package dev.fadalias.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot the hook may be reachable again: replay the offline queue and re-check the hash. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Services.get(context).syncer.syncAsync()
    }
}
