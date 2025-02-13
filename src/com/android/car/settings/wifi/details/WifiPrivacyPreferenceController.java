/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car.settings.wifi.details;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import androidx.preference.ListPreference;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.wifitrackerlib.WifiEntry;

/**
 * Shows privacy (random mac) info about the Wifi connection.
 */
public class WifiPrivacyPreferenceController extends
        WifiDetailsBasePreferenceController<ListPreference> {
    private static final Logger LOG = new Logger(WifiPrivacyPreferenceController.class);
    private String[] mPrivacyChoices;

    public WifiPrivacyPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void updateState(ListPreference preference) {
        WifiEntry wifiEntry = getWifiEntry();
        if (wifiEntry == null) {
            preference.setVisible(false);
            return;
        }
        preference.setValue(String.valueOf(wifiEntry.getPrivacy()));
        preference.setVisible(true);
        preference.setSummary(mPrivacyChoices[wifiEntry.getPrivacy()]);
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        WifiEntry wifiEntry = getWifiEntry();
        if (wifiEntry.canSetPrivacy()) {
            wifiEntry.setPrivacy(Integer.parseInt(newValue.toString()));
        } else {
            LOG.i("Can't set privacy");
        }
        return true;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        mPrivacyChoices = getContext().getResources()
                             .getStringArray(R.array.wifi_privacy_choice);
        getPreference().setValue(mPrivacyChoices[WifiEntry.PRIVACY_RANDOMIZED_MAC]);
    }
}
