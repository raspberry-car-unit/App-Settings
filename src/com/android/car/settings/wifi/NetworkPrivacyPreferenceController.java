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
package com.android.car.settings.wifi;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import androidx.preference.ListPreference;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.wifitrackerlib.WifiEntry;
import com.android.car.settings.common.PreferenceController;

/**
 * Shows privacy (random mac) info about the Wifi connection.
 */
public class NetworkPrivacyPreferenceController extends
        PreferenceController<ListPreference> {

    /** Action used in the {@link Intent} sent by the {@link LocalBroadcastManager}. */
    public static final String ACTION_PRIVACY_CHANGE =
            "com.android.car.settings.wifi.PrivacyChangeAction";
    /** Key used to store the privacy setting of the network. */
    public static final String KEY_NETWORK_PRIVACY = "privacy_setting";
    private String[] mPrivacyChoices;
    private int mPrivacy = WifiEntry.PRIVACY_RANDOMIZED_MAC;

    public NetworkPrivacyPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void updateState(ListPreference preference) {
        preference.setValue(String.valueOf(mPrivacy));
        preference.setVisible(true);
        preference.setSummary(mPrivacyChoices[mPrivacy]);
    }
    @Override
    protected boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        int mPrivacy = Integer.parseInt(newValue.toString());
        notifyPrivacyChange(mPrivacy);
        refreshUi();
        return true;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        mPrivacyChoices = getContext().getResources()
                             .getStringArray(R.array.wifi_privacy_choice);
        getPreference().setValue(mPrivacyChoices[WifiEntry.PRIVACY_RANDOMIZED_MAC]);
    }

    private void notifyPrivacyChange(int privacy) {
        Intent intent = new Intent(ACTION_PRIVACY_CHANGE);
        intent.putExtra(KEY_NETWORK_PRIVACY, privacy);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcastSync(intent);
    }
}
