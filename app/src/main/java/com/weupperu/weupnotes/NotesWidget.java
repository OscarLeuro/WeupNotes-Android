package com.weupperu.weupnotes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Weupperu.com. Online-only home shortcuts; no device note storage or local-note previews. */
public class NotesWidget extends AppWidgetProvider {
    private static PendingIntent shortcut(Context context, String action, int request) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_notes);
            views.setOnClickPendingIntent(R.id.widget_new, shortcut(context, MainActivity.ACTION_WIDGET_NOTE, 81));
            views.setOnClickPendingIntent(R.id.widget_search, shortcut(context, MainActivity.ACTION_WIDGET_SEARCH, 82));
            views.setOnClickPendingIntent(R.id.widget_open, shortcut(context, Intent.ACTION_MAIN, 83));
            manager.updateAppWidget(id, views);
        }
    }
}
