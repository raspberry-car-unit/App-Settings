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
import android.content.ComponentName;
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
import com.android.car.settings.units.AndroidKeystoreAliasLoader;

import android.security.Credentials;
import android.security.KeyStore;
import android.security.keystore.KeyProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Collectors;

import android.util.Log;

/* Notify User Certificate for connect. */

public class NetworkUserCertificatePreferenceController extends
        PreferenceController<ListPreference> {

    private static final String TAG = "NetworkUserCertificatePreferenceController";

    /** Action used in the {@link Intent} sent by the {@link LocalBroadcastManager}. */
    public static final String ACTION_USER_CERTIFICATE_CHANGED =
        "com.android.car.settings.wifi.UserCertificateChanged";

    /** Key used to store the metered choice of the network. */
    public static final String KEY_USER_CERTIFICATE_CHANGED = "usercertificate_changed";

    protected static final String SHARED_USER_CERTIFICATE = "com.android.car.settings.wifi.SHARED_USER_CERTIFICATE";
    private final SharedPreferences mSharedPreferences = getContext().getSharedPreferences(
                    NetworkPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                    Context.MODE_PRIVATE);

    private static final int SHARED_SECURITY_TYPE_UNSET = -1;

    private List<String> userCertNamesList = new ArrayList<String>();
    private List<String> userCertIdsList = new ArrayList<String>();

    private int mSelectedUserCert;
    private CharSequence[] mUserCertNames;
    private CharSequence[] mUserCertIds;

    private static final Map<Integer, Integer> USER_CERT_CHOOSE_TO_DESC_RES =
            createUserCertDescMap();

    public static final int DO_NOT_PROVIDE_CERTIFICATE = 0;

    private static final String UNDESIRED_CERTIFICATE_MACRANDSECRET = "MacRandSecret";
    private static final String UNDESIRED_CERTIFICATE_MACRANDSAPSECRET = "MacRandSapSecret";

    static final String[] UNDESIRED_CERTIFICATES = {
        UNDESIRED_CERTIFICATE_MACRANDSECRET,
        UNDESIRED_CERTIFICATE_MACRANDSAPSECRET
    };

    private Context mContext;

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

    public NetworkUserCertificatePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mContext = context;
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

        userCertNamesList.add(getContext().getString(
                USER_CERT_CHOOSE_TO_DESC_RES.get(DO_NOT_PROVIDE_CERTIFICATE)));
        userCertIdsList.add(Integer.toString(DO_NOT_PROVIDE_CERTIFICATE));

        final AndroidKeystoreAliasLoader androidKeystoreAliasLoader =
                getAndroidKeystoreAliasLoader();
        loadCertificates(androidKeystoreAliasLoader.getKeyCertAliases());
        mSharedPreferences.edit().putInt(SHARED_USER_CERTIFICATE, DO_NOT_PROVIDE_CERTIFICATE).commit();
    }

    @Override
    protected void onDestroyInternal() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mEapMethodChangeReceiver);
    }

    @Override
    protected void updateState(ListPreference preference) {
        if (mEapMethod == NetworkEapMethodPreferenceController.WIFI_EAP_METHOD_TLS ||
            mSecurityType == WifiEntry.SECURITY_EAP_SUITE_B) {
            preference.setVisible(true);
            preference.setSummary(mUserCertNames[mSelectedUserCert]);
        } else {
            preference.setVisible(false);
        }
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference,
        Object newValue) {
        mSelectedUserCert = Integer.parseInt(newValue.toString());
        getPreference().setDefaultValue(Integer.toString(mSelectedUserCert));
        mSharedPreferences.edit().putInt(SHARED_USER_CERTIFICATE, mSelectedUserCert).commit();
        notifyUserCertificateChoosed(mSelectedUserCert);
        refreshUi();
        return true;
    }

    private AndroidKeystoreAliasLoader getAndroidKeystoreAliasLoader() {
        return new AndroidKeystoreAliasLoader(KeyProperties.NAMESPACE_WIFI);
    }

    private void loadCertificates(Collection<String> choices) {
        ArrayList<String> certs = new ArrayList<String>();

        if (choices != null && choices.size() != 0) {
            certs.addAll(choices.stream()
                    .filter(certificateName -> {
                        for (String undesired : UNDESIRED_CERTIFICATES) {
                            if (certificateName.startsWith(undesired)) {
                                return false;
                             }
                        }
                        return true;
                    }).collect(Collectors.toList()));
        }

        int installedUserCertsNumber = certs.size();
        if (installedUserCertsNumber != 0) {
            for (int i = 0; i < installedUserCertsNumber; i++) {
                Log.d(TAG, "Get the UserCert " + certs.get(i));
                userCertNamesList.add(certs.get(i));
                userCertIdsList.add(Integer.toString(i + 1));
            }
        }

        mSelectedUserCert = DO_NOT_PROVIDE_CERTIFICATE;
        mUserCertNames = new CharSequence[userCertNamesList.size()];
        mUserCertNames = userCertNamesList.toArray(mUserCertNames);
        mUserCertIds = new CharSequence[userCertIdsList.size()];
        mUserCertIds = userCertIdsList.toArray(mUserCertIds);

        getPreference().setEntries(mUserCertNames);
        getPreference().setEntryValues(mUserCertIds);
        getPreference().setDefaultValue(Integer.toString(DO_NOT_PROVIDE_CERTIFICATE));
    }

    private void notifyUserCertificateChoosed(int selectedUserCert) {
        Log.d(TAG, "Selected user cert id is " + selectedUserCert);
        if (selectedUserCert != DO_NOT_PROVIDE_CERTIFICATE) {
            Intent intent = new Intent(ACTION_USER_CERTIFICATE_CHANGED);
            intent.putExtra(KEY_USER_CERTIFICATE_CHANGED, mUserCertNames[selectedUserCert]);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcastSync(intent);
        }
    }

    private static Map<Integer, Integer> createUserCertDescMap() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(DO_NOT_PROVIDE_CERTIFICATE, R.string.wifi_do_not_provide_eap_user_cert);
        return map;
    }
}
