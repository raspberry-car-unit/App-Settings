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

import androidx.annotation.VisibleForTesting;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.wifitrackerlib.WifiEntry;

/** Business logic relating to the security type and associated password. */
public class NetworkPasswordPreferenceController extends
        PreferenceController<NetworkNameRestrictedPasswordEditTextPreference> {

    private static final Logger LOG = new Logger(NetworkPasswordPreferenceController.class);

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
    final BroadcastReceiver mMeteredChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mMeteredChoice = intent.getIntExtra(
                    NetworkMeteredPreferenceController.KEY_NETWORK_METERED,
                    WifiEntry.METERED_CHOICE_AUTO);
            refreshUi();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mPrivacyChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mPrivacyChoice = intent.getIntExtra(
                    NetworkPrivacyPreferenceController.KEY_NETWORK_PRIVACY,
                    WifiEntry.PRIVACY_RANDOMIZED_MAC);
            refreshUi();
        }
    };

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final WifiManager.ActionListener mConnectionListener =
            new WifiManager.ActionListener() {
                @Override
                public void onSuccess() {
                    LOG.d("connected to network");
                    mUiHandler.post(() -> getFragmentController().goBack());
                }

                @Override
                public void onFailure(int reason) {
                    LOG.d("Failed to connect to network. Failure code: " + reason);
                    Toast.makeText(getContext(), R.string.wifi_failed_connect_message,
                            Toast.LENGTH_SHORT).show();
                }
            };

    private String mNetworkName;
    private int mSecurityType = WifiEntry.SECURITY_NONE;
    private int mMeteredChoice = WifiEntry.METERED_CHOICE_AUTO;
    private int mPrivacyChoice = WifiEntry.PRIVACY_RANDOMIZED_MAC;

    public NetworkPasswordPreferenceController(Context context, String preferenceKey,
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
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMeteredChangeReceiver,
                new IntentFilter(NetworkMeteredPreferenceController.ACTION_METERED_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mPrivacyChangeReceiver,
                new IntentFilter(NetworkPrivacyPreferenceController.ACTION_PRIVACY_CHANGE));
    }

    @Override
    protected void onDestroyInternal() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mNameChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mMeteredChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mPrivacyChangeReceiver);
    }

    @Override
    protected void updateState(NetworkNameRestrictedPasswordEditTextPreference preference) {
        if (TextUtils.isEmpty(mNetworkName)) {
            getPreference().setDialogTitle(R.string.wifi_password);
        } else {
            getPreference().setDialogTitle(mNetworkName);
        }
        preference.setVisible(!WifiUtil.isOpenNetwork(mSecurityType));
    }

    @Override
    protected boolean handlePreferenceChanged(
            NetworkNameRestrictedPasswordEditTextPreference preference, Object newValue) {
        String password = newValue.toString();
        WifiUtil.connectToWifiEntry(getContext(), mNetworkName, mSecurityType,
                password, /* hidden= */ true, mMeteredChoice, mPrivacyChoice, mConnectionListener);
        return true;
    }
}
