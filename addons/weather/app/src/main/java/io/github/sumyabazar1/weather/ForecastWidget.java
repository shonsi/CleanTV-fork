package io.github.sumyabazar1.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The home-screen widget: the chosen city, the temperature now and the next five days. Android wakes it every 30
 * minutes (forecast_widget.xml); each update is one Open-Meteo call on a short-lived thread, nothing stays running.
 */
public final class ForecastWidget extends AppWidgetProvider {
    static final int DAYS = 5;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        refresh(context.getApplicationContext(), manager, ids, goAsync());
    }

    /** Called by the app after the city changes, so the widget does not wait for its next period. */
    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ForecastWidget.class));
        if (ids.length > 0) refresh(context.getApplicationContext(), manager, ids, null);
    }

    private static void refresh(Context context, AppWidgetManager manager, int[] ids, PendingResult pending) {
        new Thread(() -> {
            try {
                RemoteViews views = build(context);
                for (int id : ids) manager.updateAppWidget(id, views);
            } finally {
                if (pending != null) pending.finish();
            }
        }, "ForecastWidget").start();
    }

    private static RemoteViews build(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_forecast);
        Intent open = new Intent(context, WeatherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.widgetRoot,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE));
        views.removeAllViews(R.id.widgetDays);

        SharedPreferences place = context.getSharedPreferences(WeatherActivity.PREFS, Context.MODE_PRIVATE);
        if (!place.contains("name")) {
            views.setTextViewText(R.id.widgetPlace, context.getString(R.string.widget_pick));
            return views;
        }
        Forecast.Report report;
        try {
            report = Forecast.fetch(Double.longBitsToDouble(place.getLong("lat", 0)),
                    Double.longBitsToDouble(place.getLong("lon", 0)), DAYS, false);
        } catch (Exception e) {
            views.setTextViewText(R.id.widgetPlace, context.getString(R.string.widget_offline));
            return views;
        }
        views.setTextViewText(R.id.widgetPlace,
                context.getString(R.string.widget_place, place.getString("name", ""), report.tempC));
        for (Forecast.Day d : report.days) {
            RemoteViews day = new RemoteViews(context.getPackageName(), R.layout.widget_day);
            day.setTextViewText(R.id.widgetDayName,
                    d.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            day.setTextViewText(R.id.widgetDaySky, Forecast.glyph(d.code));
            day.setTextViewText(R.id.widgetDayTemps, context.getString(R.string.high_low, d.maxC, d.minC));
            views.addView(R.id.widgetDays, day);
        }
        return views;
    }
}
