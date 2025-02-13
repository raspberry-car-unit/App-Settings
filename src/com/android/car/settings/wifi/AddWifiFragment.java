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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.ui.toolbar.MenuItem;
import com.android.wifitrackerlib.WifiEntry;

import java.util.Collections;
import java.util.List;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiEnterpriseConfig.Eap;
import android.net.wifi.WifiEnterpriseConfig.Phase2;

import android.util.Log;

/**
 * Adds a hidden wifi network. The connect button on the fragment is only used for unsecure hidden
 * networks. The remaining security types can be connected via pressing connect on the password
 * dialog.
 */
public class AddWifiFragment extends SettingsFragment {
    private static final String TAG = "AddWifiFragment";

    private static final Logger LOG = new Logger(AddWifiFragment.class);
    private static final String KEY_NETWORK_NAME = "network_name";
    private static final String KEY_SECURITY_TYPE = "security_type";
    private static final String KEY_NETWORK_METERED = "metered_choice";
    private static final String KEY_NETWORK_PRIVACY = "privacy_setting";
    private static final String KEY_EAP_METHOD_CHANGED = "eapmethod_changed";
    private static final String KEY_PHASE2_AUTHENTICATION_CHANGED = "phase2_changed";
    private static final String KEY_CA_CERTIFICATE_CHANGED = "cacertificate_changed";
    private static final String KEY_NETWORK_IDENTITY = "network_identity";
    private static final String KEY_NETWORK_DOMAIN = "network_domain";
    private static final String KEY_USER_CERTIFICATE_CHANGED = "usercertificate_changed";
    private static final String KEY_NETWORK_ANONYMOUS_IDENTITY = "network_anonymous_identity";
    private static final String KEY_EAP_PASSWORD_CHANGED = "eap_password_changed";
    private static final int SHARED_SECURITY_TYPE_UNSET = -1;

    private WifiConfiguration mWifiConfig;

    private static final String SYSTEM_CA_STORE_PATH = "/system/etc/security/cacerts";
    private final String mUseSystemCertsString = "Use system certificates";
    private final String mDontProvideUserCertString = "Do not provide";

    @VisibleForTesting
    final BroadcastReceiver mNameChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mNetworkName = intent.getStringExtra(
                    NetworkNamePreferenceController.KEY_NETWORK_NAME);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mSecurityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mSecurityType = intent.getIntExtra(
                    NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                    WifiEntry.SECURITY_NONE);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mMeteredChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mMeteredChoice = intent.getIntExtra(
                    NetworkMeteredPreferenceController.KEY_NETWORK_METERED,
                    WifiEntry.METERED_CHOICE_AUTO);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mPrivacyChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mPrivacyChoice = intent.getIntExtra(
                    NetworkPrivacyPreferenceController.KEY_NETWORK_PRIVACY,
                    WifiEntry.PRIVACY_RANDOMIZED_MAC);
            setButtonEnabledState();
        }
    };
    @VisibleForTesting
    final BroadcastReceiver mEapMethodChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mEapMethod = intent.getIntExtra(
                    NetworkEapMethodPreferenceController.KEY_EAP_METHOD_CHANGED,
                    NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mPhase2AuthChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mPhase2Method = intent.getIntExtra(
                    NetworkPhase2AuthenticationPreferenceController.KEY_PHASE2_AUTHENTICATION_CHANGED,
                    NetworkPhase2AuthenticationPreferenceController.WIFI_TTLS_PHASE2_PAP);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mCaCertChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mCaCertificate = intent.getStringExtra(
                    NetworkCaCertificatePreferenceController.KEY_CA_CERTIFICATE_CHANGED);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mIdentityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mIdentity = intent.getStringExtra(
                    NetworkIdentityPreferenceController.KEY_NETWORK_IDENTITY);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mDomainChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mDomain = intent.getStringExtra(
                    NetworkDomainPreferenceController.KEY_NETWORK_DOMAIN);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mAnonymousIdentityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mAnonymousIdentity = intent.getStringExtra(
                    NetworkAnonymousIdentityPreferenceController.KEY_NETWORK_ANONYMOUS_IDENTITY);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mUserCertChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mUserCertificate = intent.getStringExtra(
                    NetworkUserCertificatePreferenceController.KEY_USER_CERTIFICATE_CHANGED);
            setButtonEnabledState();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mEapPasswordChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mEapPassword = intent.getStringExtra(
                    NetworkPasswordPreferenceController.KEY_EAP_PASSWORD_CHANGED);
            setButtonEnabledState();
        }
    };

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final WifiManager.ActionListener mConnectionListener =
            new WifiManager.ActionListener() {
                @Override
                public void onSuccess() {
                    LOG.d("connected to network");
                    mUiHandler.post(() -> goBack());
                }

                @Override
                public void onFailure(int reason) {
                    LOG.d("Failed to connect to network. Failure code: " + reason);
                    Toast.makeText(getContext(), R.string.wifi_failed_connect_message,
                            Toast.LENGTH_SHORT).show();
                }
            };

    private MenuItem mAddWifiButton;
    private String mNetworkName;
    private int mMeteredChoice = WifiEntry.METERED_CHOICE_AUTO;
    private int mPrivacyChoice = WifiEntry.PRIVACY_RANDOMIZED_MAC;
    private int mSecurityType = WifiEntry.SECURITY_NONE;
    private int mEapMethod = NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS;
    private int mPhase2Method = NetworkPhase2AuthenticationPreferenceController.WIFI_TTLS_PHASE2_PAP;
    private String mCaCertificate;
    private String mIdentity;
    private String mDomain;
    private String mAnonymousIdentity;
    private String mUserCertificate;
    private String mEapPassword;

    @Override
    public List<MenuItem> getToolbarMenuItems() {
        return Collections.singletonList(mAddWifiButton);
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.add_wifi_fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mNetworkName = savedInstanceState.getString(KEY_NETWORK_NAME);
            mSecurityType = savedInstanceState.getInt(KEY_SECURITY_TYPE, WifiEntry.SECURITY_NONE);
        }

        mAddWifiButton = new MenuItem.Builder(getContext())
                .setTitle(R.string.wifi_setup_connect)
                .setOnClickListener(i -> {
                    if (mSecurityType == WifiEntry.SECURITY_NONE) {
                        Log.d(TAG, "connect to open AP " + mNetworkName);
                        // This only needs to handle hidden/unsecure networks.
                        WifiUtil.connectToWifiEntry(getContext(), mNetworkName,
                                mSecurityType, /* password= */ null, /* hidden= */ true,
                                mMeteredChoice, mPrivacyChoice,
                                mConnectionListener);
                    } else if (mSecurityType == WifiEntry.SECURITY_EAP) {
                        Log.d(TAG, "Connect to EAP AP " + mNetworkName);
                        WifiUtil.connectToEAPWifiEntry(getContext(), mNetworkName,
                                mSecurityType, mWifiConfig, /* hidden= */ true,
                                mMeteredChoice, mPrivacyChoice,
                                mConnectionListener);
                    }
                })
                .build();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mNameChangeReceiver,
                new IntentFilter(NetworkNamePreferenceController.ACTION_NAME_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mSecurityChangeReceiver,
                new IntentFilter(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mMeteredChangeReceiver,
                new IntentFilter(NetworkMeteredPreferenceController.ACTION_METERED_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mPrivacyChangeReceiver,
                new IntentFilter(NetworkPrivacyPreferenceController.ACTION_PRIVACY_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mEapMethodChangeReceiver,
                new IntentFilter(NetworkEapMethodPreferenceController.ACTION_EAPMETHOD_CHANGED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mPhase2AuthChangeReceiver,
                new IntentFilter(NetworkPhase2AuthenticationPreferenceController.ACTION_PHASE2_CHANGED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mCaCertChangeReceiver,
                new IntentFilter(NetworkCaCertificatePreferenceController.ACTION_CA_CERTIFICATE_CHANGED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mIdentityChangeReceiver,
                new IntentFilter(NetworkIdentityPreferenceController.ACTION_IDENTITY_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mDomainChangeReceiver,
                new IntentFilter(NetworkDomainPreferenceController.ACTION_DOMAIN_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mAnonymousIdentityChangeReceiver,
                new IntentFilter(NetworkAnonymousIdentityPreferenceController.ACTION_ANONYMOUS_IDENTITY_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mUserCertChangeReceiver,
                new IntentFilter(NetworkUserCertificatePreferenceController.ACTION_USER_CERTIFICATE_CHANGED));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mEapPasswordChangeReceiver,
                new IntentFilter(NetworkPasswordPreferenceController.ACTION_EAP_PASSWORD_CHANGED));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mNameChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mMeteredChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mPrivacyChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mEapMethodChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mPhase2AuthChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mCaCertChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mIdentityChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mDomainChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mAnonymousIdentityChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mUserCertChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mEapPasswordChangeReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_NETWORK_NAME, mNetworkName);
        outState.putInt(KEY_SECURITY_TYPE, mSecurityType);
    }

    @Override
    public void onStart() {
        super.onStart();
        setButtonEnabledState();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAddWifiButton != null) {
            mAddWifiButton.setEnabled(false);
        }
    }

    private void setButtonEnabledState() {
        if (mAddWifiButton != null) {
            mAddWifiButton.setEnabled(
                    !TextUtils.isEmpty(mNetworkName) && WifiUtil.isOpenNetwork(mSecurityType));
            if (mSecurityType == WifiEntry.SECURITY_EAP) {
                mWifiConfig = getConfig();
                if (mWifiConfig != null) {
                    mAddWifiButton.setEnabled(true);
                }
            }
        }
    }

    private WifiConfiguration getConfig() {
        WifiConfiguration config = new WifiConfiguration();

        if (mNetworkName.equals("")) {
            Log.e(TAG, "Didn't set SSID, please set AP's SSID!");
            return null;
        } else {
            config.SSID = "\"" + mNetworkName + "\"";
        }

        switch (mSecurityType) {
            case WifiEntry.SECURITY_EAP:
            case WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
                config.enterpriseConfig = new WifiEnterpriseConfig();
                switch (mEapMethod) {
                    // set EAP method
                    case NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TTLS:
                        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TTLS);
                        // set Phase2 Method
                        switch (mPhase2Method) {
                            case NetworkPhase2AuthenticationPreferenceController.WIFI_TTLS_PHASE2_PAP:
                                config.enterpriseConfig.setPhase2Method(Phase2.PAP);
                                break;
                            case NetworkPhase2AuthenticationPreferenceController.WIFI_TTLS_PHASE2_MSCHAP:
                                config.enterpriseConfig.setPhase2Method(Phase2.MSCHAP);
                                break;
                            case NetworkPhase2AuthenticationPreferenceController.WIFI_TTLS_PHASE2_MSCHAPV2:
                                config.enterpriseConfig.setPhase2Method(Phase2.MSCHAPV2);
                                break;
                            case NetworkPhase2AuthenticationPreferenceController.WIFI_TTLS_PHASE2_GTC:
                                config.enterpriseConfig.setPhase2Method(Phase2.GTC);
                                break;
                            default:
                                Log.e(TAG, "Unknown phase2 method" + mPhase2Method);
                                return null;
                        }
                        // set Ca Certificate path
                        config.enterpriseConfig.setCaCertificateAliases(null);
                        config.enterpriseConfig.setCaPath(null);
                        if (mCaCertificate == null) {
                           Log.d(TAG, "Didn't get ca certificate this time.");
                           return null;
                        } else if (mCaCertificate.equals(mUseSystemCertsString)) {
                            config.enterpriseConfig.setCaPath(SYSTEM_CA_STORE_PATH);
                        } else {
                            config.enterpriseConfig.setCaCertificateAliases(new String[] {mCaCertificate});
                        }
                        //set domain
                        if (mDomain == null) {
                            Log.d(TAG, "Domain hasn't been set now.");
                            return null;
                        } else {
                            config.enterpriseConfig.setDomainSuffixMatch(mDomain);
                        }
                        //set identity
                        if (mIdentity == null) {
                            Log.d(TAG, "Identity hasn't been set now.");
                            return null;
                        } else {
                            config.enterpriseConfig.setIdentity(mIdentity);
                        }
                        //set anonymous
                        if (mAnonymousIdentity == null) {
                            Log.d(TAG, "Anonymous Identity hasn't been set now.");
                            return null;
                        } else {
                            config.enterpriseConfig.setAnonymousIdentity(mAnonymousIdentity);
                        }
                        //set password
                        if (mEapPassword == null) {
                            Log.d(TAG, "Password hasn't been set now");
                            return null;
                        } else {
                            config.enterpriseConfig.setPassword(mEapPassword);
                        }
                        break;
                    case NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TLS:
                        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
                        //get the ca certificate
                        if (mCaCertificate == null) {
                            Log.d(TAG, "CA Certificate hasn't installed now");
                            return null;
                        } else if (mCaCertificate.equals(mUseSystemCertsString)) {
                            config.enterpriseConfig.setCaPath(SYSTEM_CA_STORE_PATH);
                        } else {
                            config.enterpriseConfig.setCaCertificateAliases(new String[] {mCaCertificate});
                        }
                        //set domain
                        if (mDomain == null) {
                            Log.d(TAG, "domain hasn't been set now");
                            return null;
                        } else {
                            config.enterpriseConfig.setDomainSuffixMatch(mDomain);
                        }
                        //set identity
                        if (mIdentity == null) {
                           Log.d(TAG, "Identity hasn't set now.");
                           return null;
                        } else {
                           config.enterpriseConfig.setIdentity(mIdentity);
                        }
                        //get the user certificate
                        if (mUserCertificate == null) {
                            Log.d(TAG, "Client Certificate hasn't been set now.");
                            return null;
                        } else if (mUserCertificate.equals(mDontProvideUserCertString)) {
                            Log.e(TAG, "Didn't get user certificate, it is necessary when TLS is used, plese set!");
                            return null;
                        } else {
                            config.enterpriseConfig.setClientCertificateAlias(mUserCertificate);
                        }
                        break;
                    default:
                        Log.e(TAG, "Unknown eap method " + mEapMethod);
                        break;
                }
                break;
            case WifiEntry.SECURITY_EAP_SUITE_B:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_SUITE_B);
                config.enterpriseConfig = new WifiEnterpriseConfig();
                config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
                //get the ca certificate
                if (mCaCertificate == null){
                    Log.d(TAG, "Ca Certificate hasn't been set now.");
                    return null;
                } else if (mCaCertificate.equals(mUseSystemCertsString)) {
                    config.enterpriseConfig.setCaPath(SYSTEM_CA_STORE_PATH);
                } else {
                    config.enterpriseConfig.setCaCertificateAliases(new String[] {mCaCertificate});
                }
                //set domain
                if (mDomain == null) {
                    Log.d(TAG, "Domain hasn't been set now.");
                    return null;
                } else if (mDomain.equals("")) {
                    Log.e(TAG, "Didn't get the domain, please set!");
                    return null;
                } else {
                    config.enterpriseConfig.setDomainSuffixMatch(mDomain);
                }
                //set identity
                if (mIdentity == null) {
                    Log.d(TAG, "Identity hasn't been set now.");
                    return null;
                } else if (mIdentity.equals("")) {
                    Log.e(TAG, "Didn't get the identity, please set!");
                    return null;
                } else {
                    config.enterpriseConfig.setIdentity(mIdentity);
                }
                //get the user certificate
                if (mUserCertificate == null) {
                    Log.d(TAG, "Client certificate hasn't been set now.");
                    return null;
                } else if (mUserCertificate.equals(mDontProvideUserCertString)) {
                    Log.e(TAG, "Didn't get user certificate, it is necessary when TLS is used, please set!");
                    return null;
                } else {
                    config.enterpriseConfig.setClientCertificateAlias(mUserCertificate);
                }
                break;
            default:
                break;
        }
        return config;
    }
}
