package com.android.car.settings.pi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Preference controller for setting the display orientation.
 */
public class OrientationPreferenceController extends
        PreferenceController<ListPreference> {
	
	public OrientationPreferenceController(Context context, String preferenceKey, FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
		super(context, preferenceKey, fragmentController, uxRestrictions);
	}

	@Override
	protected Class<ListPreference> getPreferenceType() {
		return ListPreference.class;
	}

	@Override
	protected boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
		int rotation = Integer.parseInt((String)newValue);
		Settings.System.putInt(getContext().getContentResolver(), Settings.System.USER_ROTATION, rotation);
		Settings.System.putInt(getContext().getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
		return true;
	}

	@Override
	protected void updateState(ListPreference preference) {
		int rotation = Settings.System.getInt(getContext().getContentResolver(), Settings.System.USER_ROTATION, 0);
		preference.setValue(String.valueOf(rotation));
	}
}
