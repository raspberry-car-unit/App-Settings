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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.wifitrackerlib.WifiEntry;
import android.content.SharedPreferences;

import android.util.Log;

/** Business logic relating to the anonymous identity for EAP connect when use TTLS. */
public class NetworkAnonymousIdentityPreferenceController extends
        PreferenceController<NetworkNameRestrictedPasswordEditTextPreference> {

    private static final String TAG = "NetworkAnonymousIdentityPreferenceController";

    private static final Logger LOG = new Logger(NetworkAnonymousIdentityPreferenceController.class);
    private static final int SHARED_SECURITY_TYPE_UNSET = -1;
    protected static final String SHARED_PREFERENCE_PATH =
             "com.android.car.settings.wifi.NetworkAnonymousIdentityPreferenceController";
    private final SharedPreferences mSharedPreferences = getContext().getSharedPreferences(SHARED_PREFERENCE_PATH, Context.MODE_PRIVATE);

    /** Action used in the {@link Intent} sent by the {@link LocalBroadcastManager}. */
    public static final String ACTION_ANONYMOUS_IDENTITY_CHANGE =
            "com.android.car.settings.wifi.AnonymousIdentityChangeAction";
    /** Key used to store the anonymous identity of the network. */
    public static final String KEY_NETWORK_ANONYMOUS_IDENTITY = "network_anonymous_identity";

    @VisibleForTesting
    final BroadcastReceiver mNameChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mNetworkName = intent.getStringExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME);
            getPreference().setNetworkName(mNetworkName);
            refreshUi();
        }
    };

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

    private String mNetworkName;
    private int mSecurityType = WifiEntry.SECURITY_NONE;
    private int mEapMethod = NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS;

    public NetworkAnonymousIdentityPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<NetworkNameRestrictedPasswordEditTextPreference> getPreferenceType() {
        return NetworkNameRestrictedPasswordEditTextPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mNameChangeReceiver,
                new IntentFilter(NetworkNamePreferenceController.ACTION_NAME_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mSecurityChangeReceiver,
                new IntentFilter(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mEapMethodChangeReceiver,
                new IntentFilter(NetworkEapMethodPreferenceController.ACTION_EAPMETHOD_CHANGED));
    }

    @Override
    protected void onDestroyInternal() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mNameChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mEapMethodChangeReceiver);
    }

    @Override
    protected void updateState(NetworkNameRestrictedPasswordEditTextPreference preference) {
        if ((mSecurityType == WifiEntry.SECURITY_EAP || mSecurityType == WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE) &&
            mEapMethod == NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS) {
            getPreference().setDialogTitle(R.string.wifi_eap_anonymous);
            preference.setSummary(TextUtils.isEmpty(preference.getText())?
                                  getContext().getString(R.string.default_anonymous):
                                  preference.getText());
            preference.setVisible(true);
        } else {
            preference.setVisible(false);
        }
    }

    @Override
    protected boolean handlePreferenceChanged(
            NetworkNameRestrictedPasswordEditTextPreference preference, Object newValue) {
        String anonymousIdentity = newValue.toString();
        preference.setText(anonymousIdentity);
        notifyIdentityChange(anonymousIdentity);
        refreshUi();
        return true;
    }

    private void notifyIdentityChange(String newAnonymousIdentity) {
        Intent intent = new Intent(ACTION_ANONYMOUS_IDENTITY_CHANGE);
        intent.putExtra(KEY_NETWORK_ANONYMOUS_IDENTITY, newAnonymousIdentity);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcastSync(intent);
    }
}
