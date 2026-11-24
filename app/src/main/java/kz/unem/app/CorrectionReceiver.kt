package kz.unem.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CorrectionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val nid = i.getStringExtra("nid") ?: return
        val cat = i.getStringExtra("cat") ?: ""
        val reply = android.app.RemoteInput.getResultsFromIntent(i)?.getCharSequence("reply")?.toString()?.trim() ?: ""
        if (cat.isEmpty() && reply.isEmpty()) return
        Store.addCorr(ctx, nid, cat, reply)
        Notifier.confirm(ctx, nid, i.getLongExtra("sum", 0), i.getStringExtra("merch") ?: "", cat, reply)
    }
}
