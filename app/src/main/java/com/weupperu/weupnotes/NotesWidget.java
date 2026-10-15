package com.weupperu.weupnotes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

/** Home screen widget: quick capture, search and a privacy-safe local note count. */
public class NotesWidget extends AppWidgetProvider {
    private static PendingIntent shortcut(Context context, String action, int request) {
        Intent intent = new Intent(context, OfflineActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NotesWidget.class));
        if (ids.length > 0) update(context, manager, ids);
    }
    private static void update(Context context, AppWidgetManager manager, int[] ids) {
        int count = -1;
        String preview = null;
        try {
            List<OfflineStore.Draft> drafts = OfflineStore.all(context);
            count = drafts.size();
            if (!drafts.isEmpty() && context.getSharedPreferences("widget",Context.MODE_PRIVATE)
                    .getBoolean("show_title",false)) preview = drafts.get(0).title;
        } catch (Exception ignored) { }
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_notes);
            views.setTextViewText(R.id.widget_count,
                count == -1 ? "Notas locales no disponibles" : count == 1 ? "1 nota local" : count + " notas locales");
            views.setTextViewText(R.id.widget_preview,
                preview == null ? "Vista previa oculta por privacidad" :
                (preview.isEmpty() ? "Sin título" : preview));
            views.setOnClickPendingIntent(R.id.widget_new, shortcut(context, OfflineActivity.ACTION_NEW, 81));
            views.setOnClickPendingIntent(R.id.widget_search, shortcut(context, OfflineActivity.ACTION_SEARCH, 82));
            views.setOnClickPendingIntent(R.id.widget_open, shortcut(context, OfflineActivity.ACTION_LIST, 83));
            manager.updateAppWidget(id, views);
        }
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) { update(context,manager,ids); }
    @Override public void onEnabled(Context context) { updateAll(context); }
}
