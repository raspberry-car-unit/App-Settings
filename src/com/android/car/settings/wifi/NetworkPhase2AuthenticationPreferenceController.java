/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.wifitrackerlib.WifiEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.SharedPreferences;

import android.util.Log;

/* Notify Phase2 Authentication for connect. */

public class NetworkPhase2AuthenticationPreferenceController extends
        PreferenceController<ListPreference> {

    private static final String TAG = "NetworkPhase2AuthenticationPreferenceController";

    /** Action used in the {@link Intent} sent by the {@link LocalBroadcastManager}. */
    public static final String ACTION_PHASE2_CHANGED =
        "com.android.car.settings.wifi.Phase2AuthenticationChanged";

    /** Key used to store the metered choice of the network. */
    public static final String KEY_PHASE2_AUTHENTICATION_CHANGED = "phase2_changed";

    protected static final String SHARED_PHASE2_AUTHENTICATION = "com.android.car.settings.wifi.SHARED_PHASE2_AUTHENTICATION";
    private final SharedPreferences mSharedPreferences = getContext().getSharedPreferences(
                    NetworkPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                    Context.MODE_PRIVATE);

    private static final int SHARED_SECURITY_TYPE_UNSET = -1;

    private int mSelectedPhase2Authentication;
    private CharSequence[] mPhase2AuthenticationNames;
    private CharSequence[] mPhase2AuthenticationIds;

    private static final Map<Integer, Integer> PHASE2_AUTH_TO_DESC_RES =
            createPhase2AuthenticationDescMap();

    public static final int WIFI_TTLS_PHASE2_PAP       = 0;
    public static final int WIFI_TTLS_PHASE2_MSCHAP    = 1;
    public static final int WIFI_TTLS_PHASE2_MSCHAPV2  = 2;
    public static final int WIFI_TTLS_PHASE2_GTC       = 3;

    @VisibleForTesting
    final BroadcastReceiver mSecurityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mSecurityType = intent.getIntExtra(
                    NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                    WifiEntry.SECURITY_NONE);
            refreshUi();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mEapMethodChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mEapMethod = intent.getIntExtra(
                    NetworkEapMethodPreferenceController.KEY_EAP_METHOD_CHANGED,
                    NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS);
            refreshUi();
        }
    };

    private int mSecurityType = WifiEntry.SECURITY_NONE;
    private int mEapMethod = NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS;

    public NetworkPhase2AuthenticationPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mSecurityChangeReceiver,
                new IntentFilter(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE));

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mEapMethodChangeReceiver,
                new IntentFilter(NetworkEapMethodPreferenceController.ACTION_EAPMETHOD_CHANGED));

        // Phase2 Authentication setup.
        List<String> phase2AuthenticationNamesList = new ArrayList<String>();
        List<String> phase2AuthenticationIdsList = new ArrayList<String>();

        phase2AuthenticationNamesList.add(getContext().getString(
                PHASE2_AUTH_TO_DESC_RES.get(WIFI_TTLS_PHASE2_PAP)));
        phase2AuthenticationIdsList.add(Integer.toString(WIFI_TTLS_PHASE2_PAP));

        phase2AuthenticationNamesList.add(getContext().getString(
                PHASE2_AUTH_TO_DESC_RES.get(WIFI_TTLS_PHASE2_MSCHAP)));
        phase2AuthenticationIdsList.add(Integer.toString(WIFI_TTLS_PHASE2_MSCHAP));

        phase2AuthenticationNamesList.add(getContext().getString(
                PHASE2_AUTH_TO_DESC_RES.get(WIFI_TTLS_PHASE2_MSCHAPV2)));
        phase2AuthenticationIdsList.add(Integer.toString(WIFI_TTLS_PHASE2_MSCHAPV2));

        phase2AuthenticationNamesList.add(getContext().getString(
                PHASE2_AUTH_TO_DESC_RES.get(WIFI_TTLS_PHASE2_GTC)));
        phase2AuthenticationIdsList.add(Integer.toString(WIFI_TTLS_PHASE2_GTC));

        mSelectedPhase2Authentication = WIFI_TTLS_PHASE2_MSCHAPV2;
        mPhase2AuthenticationNames = new CharSequence[phase2AuthenticationNamesList.size()];
        mPhase2AuthenticationNames = phase2AuthenticationNamesList.toArray(mPhase2AuthenticationNames);
        mPhase2AuthenticationIds = new CharSequence[phase2AuthenticationIdsList.size()];
        mPhase2AuthenticationIds = phase2AuthenticationIdsList.toArray(mPhase2AuthenticationIds);

        getPreference().setEntries(mPhase2AuthenticationNames);
        getPreference().setEntryValues(mPhase2AuthenticationIds);
        getPreference().setDefaultValue(Integer.toString(WIFI_TTLS_PHASE2_MSCHAPV2));

        mSharedPreferences.edit().putInt(SHARED_PHASE2_AUTHENTICATION, WIFI_TTLS_PHASE2_MSCHAPV2).commit();
    }

    @Override
    protected void onDestroyInternal() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mEapMethodChangeReceiver);
    }

    @Override
    protected void updateState(ListPreference preference) {
        if ((mSecurityType == WifiEntry.SECURITY_EAP || mSecurityType == WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE) &&
            mEapMethod == NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS) {
            preference.setVisible(true);
            preference.setSummary(PHASE2_AUTH_TO_DESC_RES.get(mSelectedPhase2Authentication));
        } else {
            preference.setVisible(false);
        }
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference,
        Object newValue) {
        mSelectedPhase2Authentication = Integer.parseInt(newValue.toString());
        notifyPhase2AuthenticationChange(mSelectedPhase2Authentication);
        refreshUi();
        return true;
    }

    private void notifyPhase2AuthenticationChange(int eapMethod) {
        Intent intent = new Intent(ACTION_PHASE2_CHANGED);
        intent.putExtra(KEY_PHASE2_AUTHENTICATION_CHANGED, eapMethod);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcastSync(intent);
    }

    private static Map<Integer, Integer> createPhase2AuthenticationDescMap() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(WIFI_TTLS_PHASE2_PAP, R.string.wifi_phase2_pap);
        map.put(WIFI_TTLS_PHASE2_MSCHAP, R.string.wifi_phase2_mschap);
        map.put(WIFI_TTLS_PHASE2_MSCHAPV2, R.string.wifi_phase2_mschapv2);
        map.put(WIFI_TTLS_PHASE2_GTC, R.string.wifi_phase2_gtc);
        return map;
    }
}
