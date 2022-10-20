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

/* Notify EAP method for connect. */

public class NetworkEapMethodPreferenceController extends
        PreferenceController<ListPreference> {

    private static final String TAG = "NetworkEapMethodPreferenceController";

    /** Action used in the {@link Intent} sent by the {@link LocalBroadcastManager}. */
    public static final String ACTION_EAPMETHOD_CHANGED =
        "com.android.car.settings.wifi.EapMethodChanged";

    /** Key used to store the metered choice of the network. */
    public static final String KEY_EAP_METHOD_CHANGED = "eapmethod_changed";

    protected static final String SHARED_EAP_METHOD = "com.android.car.settings.wifi.SHARED_EAP_METHOD";
    private final SharedPreferences mSharedPreferences = getContext().getSharedPreferences(
                    NetworkPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                    Context.MODE_PRIVATE);

    private static final int SHARED_SECURITY_TYPE_UNSET = -1;

    private int mSelectedEapMethod;
    private CharSequence[] mEapMethodNames;
    private CharSequence[] mEapMethodIds;

    private static final Map<Integer, Integer> EAP_METHOD_TO_DESC_RES =
            createEapMethodDescMap();

    public static final int WIFI_EAP_METHOD_TTLS = 0;
    public static final int WIFI_EAP_METHOD_TLS  = 1;

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

    private int mSecurityType = WifiEntry.SECURITY_NONE;

    public NetworkEapMethodPreferenceController(Context context, String preferenceKey,
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

        // EAP method setup.
        List<String> eapMethodNamesList = new ArrayList<String>();
        List<String> eapMethodIdsList = new ArrayList<String>();

        eapMethodNamesList.add(getContext().getString(
                EAP_METHOD_TO_DESC_RES.get(WIFI_EAP_METHOD_TTLS)));
        eapMethodIdsList.add(Integer.toString(WIFI_EAP_METHOD_TTLS));

        eapMethodNamesList.add(getContext().getString(
                EAP_METHOD_TO_DESC_RES.get(WIFI_EAP_METHOD_TLS)));
        eapMethodIdsList.add(Integer.toString(WIFI_EAP_METHOD_TLS));

        mSelectedEapMethod = WIFI_EAP_METHOD_TTLS;
        mEapMethodNames = new CharSequence[eapMethodNamesList.size()];
        mEapMethodNames = eapMethodNamesList.toArray(mEapMethodNames);
        mEapMethodIds = new CharSequence[eapMethodIdsList.size()];
        mEapMethodIds = eapMethodIdsList.toArray(mEapMethodIds);

        getPreference().setEntries(mEapMethodNames);
        getPreference().setEntryValues(mEapMethodIds);
        getPreference().setDefaultValue(Integer.toString(WIFI_EAP_METHOD_TTLS));

        mSharedPreferences.edit().putInt(SHARED_EAP_METHOD, WIFI_EAP_METHOD_TTLS).commit();
    }

    @Override
    protected void onDestroyInternal() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
    }

    @Override
    protected void updateState(ListPreference preference) {
        if (preference.isSelectable() == false) {
            preference.setSelectable(true);
            mSelectedEapMethod = WIFI_EAP_METHOD_TTLS;
            preference.setEnabled(true);
        }
        if (mSecurityType == WifiEntry.SECURITY_EAP ||
            mSecurityType == WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE ||
            mSecurityType == WifiEntry.SECURITY_EAP_SUITE_B) {
            preference.setVisible(true);
            if (mSecurityType == WifiEntry.SECURITY_EAP_SUITE_B) {
                mSelectedEapMethod = WIFI_EAP_METHOD_TLS;
                preference.setSelectable(false);
            }
            preference.setSummary(EAP_METHOD_TO_DESC_RES.get(mSelectedEapMethod));
        } else {
            preference.setVisible(false);
        }
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference,
        Object newValue) {
        mSelectedEapMethod = Integer.parseInt(newValue.toString());
        notifyEapMethodChange(mSelectedEapMethod);
        refreshUi();
        return true;
    }

    private void notifyEapMethodChange(int eapMethod) {
        Intent intent = new Intent(ACTION_EAPMETHOD_CHANGED);
        intent.putExtra(KEY_EAP_METHOD_CHANGED, eapMethod);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcastSync(intent);
    }

    private static Map<Integer, Integer> createEapMethodDescMap() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(WIFI_EAP_METHOD_TTLS, R.string.wifi_eap_method_ttls);
        map.put(WIFI_EAP_METHOD_TLS, R.string.wifi_eap_method_tls);
        return map;
    }
}
