package com.bildirimim.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Alarm, yeniden baslatma ve "bildirim kaydirildi" olaylarini karsilar; bildirimleri uretir. */
public class NotifReceiver extends BroadcastReceiver {
    static final String A_START = "com.bildirimim.app.START";
    static final String A_END = "com.bildirimim.app.END";
    static final String A_REPOST = "com.bildirimim.app.REPOST";
    static final String CH_ACTIVE = "aktif";
    static final String CH_END_SOUND = "bitis_sesli_v1";
    static final String CH_END_SILENT = "bitis_sessiz";
    static final int END_OFFSET = 100000;
    static final int BROWN = 0xFF3E2723;

    @Override
    public void onReceive(Context c, Intent in) {
        String a = in.getAction();
        if (a == null) return;
        int id = in.getIntExtra("id", -1);
        if (A_START.equals(a) || A_REPOST.equals(a)) {
            JSONObject o = Store.get(c, id);
            if (o == null) return;
            if (o.optLong("end") <= System.currentTimeMillis()) finish(c, o);
            else showActive(c, o);
        } else if (A_END.equals(a)) {
            JSONObject o = Store.get(c, id);
            if (o != null) finish(c, o);
        } else {
            sync(c); // BOOT_COMPLETED / MY_PACKAGE_REPLACED
        }
    }

    // ---- genel durum esitleme: acilista, telefon yeniden basladiginda ----
    static void sync(Context c) {
        channels(c);
        long now = System.currentTimeMillis();
        JSONArray a = Store.all(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (o.optLong("end") <= now) finish(c, o);
            else schedule(c, o);
        }
    }

    static void schedule(Context c, JSONObject o) {
        channels(c);
        int id = o.optInt("id");
        long now = System.currentTimeMillis();
        if (o.optLong("start") <= now) showActive(c, o);
        else alarm(c, o.optLong("start"), pi(c, A_START, id, id * 2));
        alarm(c, o.optLong("end"), pi(c, A_END, id, id * 2 + 1));
    }

    static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, A_START, id, id * 2));
        am.cancel(pi(c, A_END, id, id * 2 + 1));
        Store.remove(c, id); // once sil ki kaydirma olayi bildirimi geri getirmesin
        nm(c).cancel(id);
    }

    private static void alarm(Context c, long at, PendingIntent p) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            else
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    private static PendingIntent pi(Context c, String action, int id, int req) {
        Intent i = new Intent(c, NotifReceiver.class).setAction(action).putExtra("id", id);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static String hm(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    /** Zaman araligi boyunca duran, kaydirilsa bile geri gelen onemli bildirim. */
    static void showActive(Context c, JSONObject o) {
        channels(c);
        int id = o.optInt("id");
        String text = o.optString("text");
        long end = o.optLong("end");
        Notification.Builder b = new Notification.Builder(c, CH_ACTIVE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(text)
                .setContentText(hm(o.optLong("start")) + " – " + hm(end) + " arası aktif")
                .setStyle(new Notification.BigTextStyle().bigText(text)
                        .setSummaryText("Önemli • " + hm(end) + "'e kadar"))
                .setColor(BROWN)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(end)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentIntent(open(c))
                .setDeleteIntent(pi(c, A_REPOST, id, id * 2 + 500000));
        Notification n = b.build();
        n.flags |= Notification.FLAG_NO_CLEAR;
        try { nm(c).notify(id, n); } catch (Exception ignored) {}
    }

    /** Sure bitti: kalici bildirimi kaldir, (istenirse sesli) bitis bildirimi goster. */
    static void finish(Context c, JSONObject o) {
        channels(c);
        int id = o.optInt("id");
        Store.remove(c, id);
        nm(c).cancel(id);
        boolean sound = o.optBoolean("sound", true);
        Notification.Builder b = new Notification.Builder(c, sound ? CH_END_SOUND : CH_END_SILENT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Süre doldu")
                .setContentText(o.optString("text"))
                .setStyle(new Notification.BigTextStyle().bigText(o.optString("text")))
                .setColor(BROWN)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open(c));
        try { nm(c).notify(END_OFFSET + id, b.build()); } catch (Exception ignored) {}
    }

    static void channels(Context c) {
        NotificationManager m = nm(c);
        if (m.getNotificationChannel(CH_ACTIVE) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ACTIVE, "Aktif bildirimler", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Seçtiğiniz zaman aralığı boyunca görünen bildirimler");
            ch.setSound(null, null);
            ch.enableVibration(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            m.createNotificationChannel(ch);
        }
        if (m.getNotificationChannel(CH_END_SOUND) == null) {
            NotificationChannel ch = new NotificationChannel(CH_END_SOUND, "Süre doldu (sesli)", NotificationManager.IMPORTANCE_HIGH);
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ch.setSound(u, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 600});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            m.createNotificationChannel(ch);
        }
        if (m.getNotificationChannel(CH_END_SILENT) == null) {
            NotificationChannel ch = new NotificationChannel(CH_END_SILENT, "Süre doldu (sessiz)", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            m.createNotificationChannel(ch);
        }
    }
}
