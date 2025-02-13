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

/* Notify EAP method for connect. */

public class NetworkCaCertificatePreferenceController extends
        PreferenceController<ListPreference> {

    private static final String TAG = "NetworkCaCertificatePreferenceController";

    /** Action used in the {@link Intent} sent by the {@link LocalBroadcastManager}. */
    public static final String ACTION_CA_CERTIFICATE_CHANGED =
        "com.android.car.settings.wifi.CaCertificateChanged";

    /** Key used to store the metered choice of the network. */
    public static final String KEY_CA_CERTIFICATE_CHANGED = "cacertificate_changed";

    protected static final String SHARED_CA_CERTIFICATE = "com.android.car.settings.wifi.SHARED_CA_CERTIFICATE";
    private final SharedPreferences mSharedPreferences = getContext().getSharedPreferences(
                    NetworkPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                    Context.MODE_PRIVATE);

    private static final int SHARED_SECURITY_TYPE_UNSET = -1;

    private List<String> caCertNamesList = new ArrayList<String>();
    private List<String> caCertIdsList = new ArrayList<String>();

    private int mSelectedCaCert;
    private CharSequence[] mCaCertNames;
    private CharSequence[] mCaCertIds;

    private static final Map<Integer, Integer> CA_CERT_CHOOSE_TO_DESC_RES =
            createCaCertDescMap();

    public static final int SYSTEM_CERTIFICATE = 0;
    public static final int INSTALL_CERTIFICATE = 1;

    private static final String UNDESIRED_CERTIFICATE_MACRANDSECRET = "MacRandSecret";
    private static final String UNDESIRED_CERTIFICATE_MACRANDSAPSECRET = "MacRandSapSecret";

    static final String[] UNDESIRED_CERTIFICATES = {
        UNDESIRED_CERTIFICATE_MACRANDSECRET,
        UNDESIRED_CERTIFICATE_MACRANDSAPSECRET
    };

    /* These values are for install certificate */
    private static final String ACTION_INSTALL_CERTS = "android.credentials.INSTALL";
    private static final String PACKAGE_INSTALL_CERTS = "com.android.certinstaller";
    private static final String CLASS_INSTALL_CERTS = "com.android.certinstaller.CertInstallerMain";
    private static final String KEY_INSTALL_CERTIFICATE = "certificate_install_usage";
    private static final String INSTALL_CERTIFICATE_VALUE = "wifi";

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

    private int mSecurityType = WifiEntry.SECURITY_NONE;

    public NetworkCaCertificatePreferenceController(Context context, String preferenceKey,
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

        caCertNamesList.add(getContext().getString(
                CA_CERT_CHOOSE_TO_DESC_RES.get(SYSTEM_CERTIFICATE)));
        caCertIdsList.add(Integer.toString(SYSTEM_CERTIFICATE));

        caCertNamesList.add(getContext().getString(
                CA_CERT_CHOOSE_TO_DESC_RES.get(INSTALL_CERTIFICATE)));
        caCertIdsList.add(Integer.toString(INSTALL_CERTIFICATE));

        final AndroidKeystoreAliasLoader androidKeystoreAliasLoader =
                getAndroidKeystoreAliasLoader();
        loadCertificates(androidKeystoreAliasLoader.getCaCertAliases());
        mSharedPreferences.edit().putInt(SHARED_CA_CERTIFICATE, SYSTEM_CERTIFICATE).commit();
    }

    @Override
    protected void onDestroyInternal() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
    }

    @Override
    protected void updateState(ListPreference preference) {
        if (mSecurityType == WifiEntry.SECURITY_EAP ||
            mSecurityType == WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE ||
            mSecurityType == WifiEntry.SECURITY_EAP_SUITE_B) {
            preference.setVisible(true);
            preference.setSummary(mCaCertNames[mSelectedCaCert]);
        } else {
            preference.setVisible(false);
        }
    }

    @Override
    protected boolean handlePreferenceChanged(ListPreference preference,
        Object newValue) {
        mSelectedCaCert = Integer.parseInt(newValue.toString());
        if (mSelectedCaCert == INSTALL_CERTIFICATE) {
            startActivityForInstallCerts();
            final AndroidKeystoreAliasLoader androidKeystoreAliasLoader =
                    getAndroidKeystoreAliasLoader();
            loadCertificates(androidKeystoreAliasLoader.getCaCertAliases());

        }
        getPreference().setDefaultValue(Integer.toString(mSelectedCaCert));
        mSharedPreferences.edit().putInt(SHARED_CA_CERTIFICATE, mSelectedCaCert).commit();
        notifyCaCertificateChoosed(mSelectedCaCert);
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

        int installedCaCertsNumber = certs.size();
        if (installedCaCertsNumber != 0) {
            for (int i = 0; i < installedCaCertsNumber; i++) {
                caCertNamesList.add(certs.get(i));
                caCertIdsList.add(Integer.toString(i + 2));
            }
        }

        mSelectedCaCert = SYSTEM_CERTIFICATE;
        mCaCertNames = new CharSequence[caCertNamesList.size()];
        mCaCertNames = caCertNamesList.toArray(mCaCertNames);
        mCaCertIds = new CharSequence[caCertIdsList.size()];
        mCaCertIds = caCertIdsList.toArray(mCaCertIds);

        getPreference().setEntries(mCaCertNames);
        getPreference().setEntryValues(mCaCertIds);
        getPreference().setDefaultValue(Integer.toString(SYSTEM_CERTIFICATE));
    }

    /**
     * Start the install page for user to install the existing certificate.
     */
    @VisibleForTesting
    void startActivityForInstallCerts() {
        Intent intent = new Intent(ACTION_INSTALL_CERTS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(new ComponentName(PACKAGE_INSTALL_CERTS, CLASS_INSTALL_CERTS));
        intent.putExtra(KEY_INSTALL_CERTIFICATE, INSTALL_CERTIFICATE_VALUE);

        mContext.startActivity(intent);
    }

    private void notifyCaCertificateChoosed(int selectedCaCert) {
        if (selectedCaCert != INSTALL_CERTIFICATE) {
            Intent intent = new Intent(ACTION_CA_CERTIFICATE_CHANGED);
            intent.putExtra(KEY_CA_CERTIFICATE_CHANGED, mCaCertNames[selectedCaCert]);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcastSync(intent);
        }
    }

    private static Map<Integer, Integer> createCaCertDescMap() {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(SYSTEM_CERTIFICATE, R.string.wifi_use_system_certs);
        map.put(INSTALL_CERTIFICATE, R.string.wifi_install_credentials);
        return map;
    }
}
