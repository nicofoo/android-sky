/*
 * Copyright (C) 2009 Jeff Sharkey, http://jsharkey.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jsharkey.sky;

import org.jsharkey.sky.ForecastProvider.AppWidgets;
import org.jsharkey.sky.ForecastProvider.AppWidgetsColumns;
import org.jsharkey.sky.ForecastProvider.Forecasts;
import org.jsharkey.sky.ForecastProvider.ForecastsColumns;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils.TruncateAt;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

/**
 * Dialog activity to show the detailed forecast behind a given widget. The
 * widget is specified by sending a {@link Intent#setData(Uri)} pointing towards
 * a valid {@link AppWidgets#CONTENT_TYPE}.
 */
public class DetailsActivity extends ListActivity implements View.OnClickListener, AdapterView.OnItemClickListener {
	private static final String TAG = "DetailsActivity";

	private Uri mData;
	private ListAdapter mAdapter;

	private static final String[] PROJECTION_APPWIDGET = { BaseColumns._ID, AppWidgetsColumns.TITLE,
			AppWidgetsColumns.TEMP_UNIT, };

	private static final int COL_TITLE = 1;
	private static final int COL_TEMP_UNIT = 2;

	private static final String[] PROJECTION_FORECAST = new String[] { BaseColumns._ID, ForecastsColumns.VALID_START,
			ForecastsColumns.TEMP_HIGH, ForecastsColumns.TEMP_LOW, ForecastsColumns.CONDITIONS, ForecastsColumns.URL,
			ForecastsColumns.ICON_URL };

	private static final int COL_VALID_START = 1;
	private static final int COL_TEMP_HIGH = 2;
	private static final int COL_TEMP_LOW = 3;
	private static final int COL_CONDITIONS = 4;
	private static final int COL_URL = 5;
	private static final int COL_ICON_URL = 6;

	private String temp_unit_str = "";

	private int widget_id;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.details);

		// Assign correct formatting to dialog title bar. There is a resources
		// bug that prevents us from defining this in R.styles directly.
		View title = findViewById(android.R.id.title);
		if (title instanceof TextView) {
			TextView titleText = (TextView) title;
			int dialogPadding = (int) getResources().getDimension(R.dimen.dialog_padding);
			titleText.setSingleLine();
			titleText.setEllipsize(TruncateAt.END);
			titleText.setGravity(Gravity.CENTER_VERTICAL);
			titleText.setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding);
			titleText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_dialog_menu_generic, 0, 0, 0);
			titleText.setCompoundDrawablePadding(dialogPadding);
			titleText.setOnClickListener(this);
		}

		// Use provided data to figure out which widget was selected
		final Intent intent = getIntent();
		mData = intent.getData();
		if (mData == null) {
			finish();
			return;
		} else {
			Log.d(TAG, "Showing details for data=" + mData);
		}

		// Pull widget title and desired units
		ContentResolver resolver = getContentResolver();
		Cursor cursor = null;

		try {
			cursor = resolver.query(mData, PROJECTION_APPWIDGET, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {

				widget_id = Integer.parseInt(cursor.getString(0));

				String titleString = cursor.getString(COL_TITLE);
				setTitle(getString(R.string.detail_title, titleString));
				temp_unit_str = cursor.getString(COL_TEMP_UNIT);

			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}

		// Query for any matching forecast data and create adapter
		Uri forecastUri = Uri.withAppendedPath(mData, AppWidgets.TWIG_FORECASTS);
		Cursor forecastCursor = managedQuery(forecastUri, PROJECTION_FORECAST, null, null, null);

		mAdapter = new ForecastAdapter(this, forecastCursor);
		setListAdapter(mAdapter);
		getListView().setOnItemClickListener(this);

	}

	/**
	 * When clicked, launch any associated {@link ForecastsColumns#URL}.
	 */
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Cursor cursor = (Cursor) parent.getItemAtPosition(position);
		String url = cursor.getString(COL_URL);

		if (url != null) {
			Intent linkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			startActivity(linkIntent);
		}
	}

	/**
	 * Adapter to show data from a {@link Forecasts#CONTENT_URI} cursor. Binds
	 * various {@link ForecastsColumns} values into list items.
	 */
	private class ForecastAdapter extends ResourceCursorAdapter {
		private final Resources mResources;
		private final Time mTime = new Time();

		/**
		 * @param cursor
		 *            Valid cursor with entries from
		 *            {@link Forecasts#CONTENT_URI}.
		 */
		public ForecastAdapter(Context context, Cursor cursor) {
			super(context, R.layout.details_item, cursor);
			mResources = context.getResources();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			// Locate all views we're about to fill
			ImageView icon = (ImageView) view.findViewById(R.id.icon);
			TextView day = (TextView) view.findViewById(R.id.day);
			TextView conditions = (TextView) view.findViewById(R.id.conditions);
			TextView high = (TextView) view.findViewById(R.id.high);
			TextView low = (TextView) view.findViewById(R.id.low);

			ContentResolver resolver = context.getContentResolver();
			Resources res = context.getResources();

			// Figure out day-of-week acronym to use
			mTime.set(cursor.getLong(COL_VALID_START));
			String dayOfWeek = DateUtils.getDayOfWeekString(mTime.weekDay + 1, DateUtils.LENGTH_MEDIUM).toUpperCase();
			day.setText(dayOfWeek);

			// Set forecast conditions string
			String conditionsString = cursor.getString(COL_CONDITIONS);
			conditions.setText(conditionsString);

			// Always assume daytime for list icons
			String icon_url = cursor.getString(COL_ICON_URL);
			int iconResource = ForecastUtils.getIconForForecast(icon_url, true);
			icon.setImageResource(iconResource);

			// Format and insert temperature values, if found
			int tempHigh = cursor.getInt(COL_TEMP_HIGH);
			int tempLow = cursor.getInt(COL_TEMP_LOW);

			if (tempHigh == Integer.MIN_VALUE || tempLow == Integer.MIN_VALUE) {
				high.setText(null);
				low.setText(null);
			} else {
				high.setText(((Integer) tempHigh).toString() + temp_unit_str);
				low.setText(((Integer) tempLow).toString() + temp_unit_str);
			}
		}
	}

	@Override
	public void onClick(View v) {

		// Request update for these widgets and launch updater service
		UpdateService.requestUpdate(new int[] { widget_id });
		startService(new Intent(this, UpdateService.class));

		finish();

	}
}
