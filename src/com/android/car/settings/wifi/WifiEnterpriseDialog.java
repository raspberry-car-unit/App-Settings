/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import android.content.DialogInterface;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiDialogFragment;
import com.android.wifitrackerlib.WifiEntry;
import com.android.car.settings.units.AndroidKeystoreAliasLoader;

import android.security.keystore.KeyProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.stream.Collectors;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;

import android.util.Log;

/** Enterprise dialog for {@link WifiEntry}. */
public class WifiEnterpriseDialog extends CarUiDialogFragment
        implements AdapterView.OnItemSelectedListener {
    public static final String TAG = "WifiEnterpriseDialog";
    private final WifiEntry mWifiEntry;
    private final WifiDialogListener mListener;
    private EditText mEditText;
    private String[] mMeteredChoices;
    private int mMetered = WifiEntry.METERED_CHOICE_AUTO;
    private int mPrivacy = WifiEntry.PRIVACY_RANDOMIZED_MAC;

    private WifiConfiguration mWifiConfig;

    private Context mContext;
    private View mView;
    private Spinner mEapMethodSpinner;
    private Spinner mPhase2Spinner;
    private Spinner mEapCaCertSpinner;
    private Spinner mEapUserCertSpinner;

    private EditText mIdentity;
    private EditText mAnonymous;
    private EditText mDomain;
    private EditText mEapPassword;

    /* These values come from "wifi_eap_method" resource array */
    public static final int WIFI_EAP_METHOD_TLS  = 1;
    public static final int WIFI_EAP_METHOD_TTLS = 0;

    /* These values come from "wifi_ttls_phase2_entries" resource array */
    public static final int WIFI_TTLS_PHASE2_PAP       = 0;
    public static final int WIFI_TTLS_PHASE2_MSCHAP    = 1;
    public static final int WIFI_TTLS_PHASE2_MSCHAPV2  = 2;
    public static final int WIFI_TTLS_PHASE2_GTC       = 3;

    /* These values use to display certificate relative information */
    private String mUnspecifiedCertString;
    private String mMultipleCertSetString;
    private String mUseSystemCertsString;
    private String mDoNotProvideEapUserCertString;
    private String mInstallCertsString;

    private static final String SYSTEM_CA_STORE_PATH = "/system/etc/security/cacerts";

    private static final String UNDESIRED_CERTIFICATE_MACRANDSECRET = "MacRandSecret";
    private static final String UNDESIRED_CERTIFICATE_MACRANDSAPSECRET = "MacRandSapSecret";

    /* These values are for install certificate */
    private static final String ACTION_INSTALL_CERTS = "android.credentials.INSTALL";
    private static final String PACKAGE_INSTALL_CERTS = "com.android.certinstaller";
    private static final String CLASS_INSTALL_CERTS = "com.android.certinstaller.CertInstallerMain";
    private static final String KEY_INSTALL_CERTIFICATE = "certificate_install_usage";
    private static final String INSTALL_CERTIFICATE_VALUE = "wifi";

    static final String[] UNDESIRED_CERTIFICATES = {
        UNDESIRED_CERTIFICATE_MACRANDSECRET,
        UNDESIRED_CERTIFICATE_MACRANDSAPSECRET
    };

    /**
     * Host UI component of WifiDialog can receive callbacks by this interface.
     */
    public interface WifiDialogListener {
        /**
         * To save the Wi-Fi network.
         */
        default void onSubmit(WifiEnterpriseDialog dialog) {
        }
    }

    public WifiEnterpriseDialog(@NonNull WifiEntry wifiEntry, @Nullable WifiDialogListener listener) {
        super();
        mListener = listener;
        mWifiEntry = wifiEntry;
        mContext = getContext();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialogTitle = mWifiEntry.getSsid();
        mDialogLayoutRes = R.layout.preference_dialog_secure_network;
        mPositiveButtonText = getContext().getString(R.string.okay);
        mNegativeButtonText = getContext().getString(R.string.cancel);
        mMeteredChoices = getContext().getResources()
                            .getStringArray(R.array.wifi_metered_spinner_choice);
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);

        mEapPassword = view.findViewById(android.R.id.edit);
        mEapPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        showEnterprise(view);
        CheckBox cb = view.findViewById(R.id.checkbox);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mEapPassword.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    mEapPassword.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                // Place cursor at the end
                mEapPassword.setSelection(mEditText.getText().length());
            }
        });

        Spinner meteredSpinner = view.findViewById(R.id.meterspinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                             R.layout.wifi_meter_spinner, mMeteredChoices);
        adapter.setDropDownViewResource(R.layout.wifi_meter_spinner_dropdown);
        meteredSpinner.setAdapter(adapter);
        meteredSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mMetered = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                /* Do nothing */
            }
        });

        CheckBox privacyCheckbox = view.findViewById(R.id.privacybox);
        privacyCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mPrivacy = WifiEntry.PRIVACY_RANDOMIZED_MAC;
                } else {
                    mPrivacy = WifiEntry.PRIVACY_DEVICE_MAC;
                }
            }
        });
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (mListener != null) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                mListener.onSubmit(this);
            }
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        setEAPConfiguration();
        if (mListener != null) {
            mListener.onSubmit(this);
        }
    }

    public WifiEntry getWifiEntry() {
        return mWifiEntry;
    }

    /**
     * @return {@link WifiConfiguration} from mWifiEntry and UI edit result
     */
    public WifiConfiguration getConfig() {
        return WifiUtil.getWifiConfig(mWifiEntry, mEditText.getText().toString(),
                                      mMetered, mPrivacy);
    }

    /* Set the initialize status to EAP-TTLS */
    public void showEnterprise(@NonNull View view) {
        mView = view;
        view.findViewById(R.id.eap).setVisibility(View.VISIBLE);
        initEapMethodSpinner(view);
        initPhase2AuthenticationSpinner(view);

        /* Give text to certificate text*/
        mUnspecifiedCertString = getContext().getString(R.string.wifi_unspecified);
        mMultipleCertSetString = getContext().getString(R.string.wifi_multiple_cert_added);
        mUseSystemCertsString = getContext().getString(R.string.wifi_use_system_certs);
        mDoNotProvideEapUserCertString =
            getContext().getString(R.string.wifi_do_not_provide_eap_user_cert);
        mInstallCertsString = getContext().getString(R.string.wifi_install_credentials);
        initCertificatesSpinner(view);
        showEapFieldsByMethod(mEapMethodSpinner.getSelectedItemPosition());
    }

    public int getPhase2AuthenticationNumber(String Phase2Authentication) {

        if (Phase2Authentication.equals("PAP")) {
            return WifiEnterpriseConfig.Phase2.PAP;
        } else if (Phase2Authentication.equals("MSCHAP")) {
            return WifiEnterpriseConfig.Phase2.MSCHAP;
        } else if (Phase2Authentication.equals("MSCHAPV2")) {
            return WifiEnterpriseConfig.Phase2.MSCHAPV2;
        } else if (Phase2Authentication.equals("GTC")) {
            return WifiEnterpriseConfig.Phase2.GTC;
        } else {
            return WifiEnterpriseConfig.Phase2.MSCHAPV2;
        }
    }

    public void setEAPConfiguration() {
        //Get final EAP method use
        String EapMethod = mEapMethodSpinner.getSelectedItem().toString();
        Log.d(TAG, "Final EAP method used to connect is " + EapMethod);

        WifiConfiguration config = mWifiEntry.getWifiConfiguration();
        if (config == null) {
            Log.d(TAG, "create new wifi configuration for current AccessPoint");
            config = new WifiConfiguration();
            config.SSID = "\"" + mWifiEntry.getSsid() + "\"";
            Log.d(TAG, "Set config's SSID " + config.SSID);
        } else {
            config.SSID = "\"" + mWifiEntry.getSsid() + "\"";
        }

        int security = mWifiEntry.getSecurity();
        Log.d(TAG, "security is " + security);
        if (security == WifiEntry.SECURITY_EAP ||
            security == WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE ||
            security == WifiEntry.SECURITY_EAP_SUITE_B) {
            if (security == WifiEntry.SECURITY_EAP) {
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
            } else if (security == WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE) {
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
            } else {
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_SUITE_B);
            }
            Log.d(TAG, "Create enterprise configuration for this AP");
            config.enterpriseConfig = new WifiEnterpriseConfig();
        }

        //Set EAP config based on EAP method used
        if (EapMethod.equals("TTLS")) {
            //Set EAP Method at first
            config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TTLS);

            //Set other TTLS relative parameters

            //Get Phase2 Authentication used
            String Phase2Authentication = mPhase2Spinner.getSelectedItem().toString();
            Log.d(TAG, "Final Phase2 Authentication is " + Phase2Authentication);
            config.enterpriseConfig.setPhase2Method(getPhase2AuthenticationNumber(Phase2Authentication));

            //Get Ca Certificate
            String caCert = (String) mEapCaCertSpinner.getSelectedItem();
            config.enterpriseConfig.setCaCertificateAliases(null);
            config.enterpriseConfig.setCaPath(null);
            if (caCert.equals(mUnspecifiedCertString)) {
                Log.d(TAG, "CA certificate choose do not priovde, do nothing");
            } else if (caCert.equals(mUseSystemCertsString)) {
                Log.d(TAG, "CA certificate choose system pre installed. Set ca path");
                config.enterpriseConfig.setCaPath(SYSTEM_CA_STORE_PATH);
            } else if (caCert.equals(mMultipleCertSetString)) {
                Log.d(TAG, "CA certificate choose multiple cert set, only available when AP is saved");
            } else {
                Log.d(TAG, "Set installed CA certificate " + caCert);
                config.enterpriseConfig.setCaCertificateAliases(new String[] {caCert});
            }

            // ca_cert or ca_path should not both be non-null, since we only intend to let
            // the use either their own certificate, or the system certificates, not both.
            // The variable that is not used must explicitly be set to null, so that a
            // previously-set value on a saved configuration will be erased on an update.
            if (config.enterpriseConfig.getCaCertificateAliases() != null
                && config.enterpriseConfig.getCaPath() != null) {
                Log.e(TAG, "ca_cert ("
                          + config.enterpriseConfig.getCaCertificateAliases()
                          + ") and ca_path ("
                          + config.enterpriseConfig.getCaPath()
                          + ") should not both be non-null");
            }

            //Get domain
            mDomain = (EditText)mView.findViewById(R.id.domain);
            String Domain = mDomain.getText().toString();
            Log.d(TAG, "Domain is " + Domain);
            config.enterpriseConfig.setDomainSuffixMatch(Domain);

            //Get Identity
            mIdentity = (EditText)mView.findViewById(R.id.identity);
            String Identity = mIdentity.getText().toString();
            Log.d(TAG, "Identity is " + Identity);
            config.enterpriseConfig.setIdentity(Identity);

            //Get Anonymous Identity
            mAnonymous = (EditText)mView.findViewById(R.id.anonymous);
            String Anonymous = mAnonymous.getText().toString();
            Log.d(TAG, "Anonymous is " + Anonymous);
            config.enterpriseConfig.setAnonymousIdentity(Anonymous);

            //Get EAP password
            mEapPassword = (EditText)mView.findViewById(android.R.id.edit);
            String EapPassword = mEapPassword.getText().toString();
            config.enterpriseConfig.setPassword(EapPassword);
        } else if (EapMethod.equals("TLS")) {
            //Set EAP Method at first
            config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);

            //Set other TLS relative parameters

            //Get Ca Certificate
            String caCert = (String) mEapCaCertSpinner.getSelectedItem();
            config.enterpriseConfig.setCaCertificateAliases(null);
            config.enterpriseConfig.setCaPath(null);
            if (caCert.equals(mUnspecifiedCertString)) {
                Log.d(TAG, "CA certificate choose do not priovde, do nothing");
            } else if (caCert.equals(mUseSystemCertsString)) {
                Log.d(TAG, "CA certificate choose system pre installed. Set ca path");
                config.enterpriseConfig.setCaPath(SYSTEM_CA_STORE_PATH);
            } else if (caCert.equals(mMultipleCertSetString)) {
                Log.d(TAG, "CA certificate choose multiple cert set, only available when AP is saved");
            } else {
                Log.d(TAG, "Set installed CA certificate " + caCert);
                config.enterpriseConfig.setCaCertificateAliases(new String[] {caCert});
            }

            // ca_cert or ca_path should not both be non-null, since we only intend to let
            // the use either their own certificate, or the system certificates, not both.
            // The variable that is not used must explicitly be set to null, so that a
            // previously-set value on a saved configuration will be erased on an update.
            if (config.enterpriseConfig.getCaCertificateAliases() != null
                 && config.enterpriseConfig.getCaPath() != null) {
                 Log.e(TAG, "ca_cert ("
                          + config.enterpriseConfig.getCaCertificateAliases()
                          + ") and ca_path ("
                          + config.enterpriseConfig.getCaPath()
                          + ") should not both be non-null");
            }

            //Get clientCert
            String clientCert = (String) mEapUserCertSpinner.getSelectedItem();
            if (clientCert.equals(mUnspecifiedCertString)
                   || clientCert.equals(mDoNotProvideEapUserCertString)) {
                // Note: |clientCert| should not be able to take the value |unspecifiedCert|,
                // since we prevent such configurations from being saved.
                clientCert = "";
            }
            Log.d(TAG, "Set client certificate " + clientCert);
            config.enterpriseConfig.setClientCertificateAlias(clientCert);

            //Get domain
            mDomain = (EditText)mView.findViewById(R.id.domain);
            String Domain = mDomain.getText().toString();
            Log.d(TAG, "Domain is " + Domain);
            config.enterpriseConfig.setDomainSuffixMatch(Domain);

            //Get Identity
            mIdentity = (EditText)mView.findViewById(R.id.identity);
            String Identity = mIdentity.getText().toString();
            Log.d(TAG, "Identity is " + Identity);
            config.enterpriseConfig.setIdentity(Identity);
        }
        mWifiConfig = config;
    }

    public WifiConfiguration getWifiConfiguration() {
        return mWifiConfig;
    }

    private ArrayAdapter<CharSequence> getSpinnerAdapter(
            int contentStringArrayResId) {
        return getSpinnerAdapter(
                mContext.getResources().getStringArray(contentStringArrayResId));
    }

    private ArrayAdapter<CharSequence> getSpinnerAdapter(
            String[] contentStringArray) {
        ArrayAdapter<CharSequence> spinnerAdapter = new ArrayAdapter<>(mContext,
                 android.R.layout.simple_spinner_item, contentStringArray);
        spinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        return spinnerAdapter;
    }

    private void initEapMethodSpinner(View view) {
        Log.d(TAG, "initialize eap method spinner");
        String[] eapMethodSpinnerAdapter;
        mEapMethodSpinner = (Spinner)view.findViewById(R.id.eap_method_edit);
        mEapMethodSpinner.setOnItemSelectedListener(this);
        eapMethodSpinnerAdapter = getContext().getResources().
                getStringArray(R.array.wifi_eap_method);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_spinner_item, eapMethodSpinnerAdapter);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mEapMethodSpinner.setAdapter(adapter);
        mEapMethodSpinner.setEnabled(true);
        if (mWifiEntry.getSecurity() == WifiEntry.SECURITY_EAP_SUITE_B) {
            mEapMethodSpinner.setSelection(WIFI_EAP_METHOD_TLS);
            mEapMethodSpinner.setEnabled(false);
        } else {
            mEapMethodSpinner.setSelection(WIFI_EAP_METHOD_TTLS);
        }
    }

    private void initPhase2AuthenticationSpinner(View view) {
        Log.d(TAG, "Initialize Phase2 Authentication Spinner");
        String[] phase2SpinnerAdapter;
        mPhase2Spinner = (Spinner)view.findViewById(R.id.phase2_authentication_edit);
        mPhase2Spinner.setOnItemSelectedListener(this);
        phase2SpinnerAdapter = getContext().getResources().
                getStringArray(R.array.wifi_ttls_phase2_entries);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_spinner_item, phase2SpinnerAdapter);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mPhase2Spinner.setAdapter(adapter);
        mPhase2Spinner.setEnabled(true);
        mPhase2Spinner.setSelection(WIFI_TTLS_PHASE2_MSCHAPV2);
    }

    private void initCertificatesSpinner(View view) {
        Log.d(TAG, "Initialize Ca Certificate and User Certificate Spinner");
        mEapCaCertSpinner = (Spinner) view.findViewById(R.id.ca_cert);
        mEapCaCertSpinner.setOnItemSelectedListener(this);

        mEapUserCertSpinner = (Spinner) view.findViewById(R.id.user_cert);
        mEapUserCertSpinner.setOnItemSelectedListener(this);

        final AndroidKeystoreAliasLoader androidKeystoreAliasLoader =
                getAndroidKeystoreAliasLoader();

        loadCertificates(
                mEapCaCertSpinner,
                androidKeystoreAliasLoader.getCaCertAliases(),
                null /* noCertificateString */,
                false /* showMultipleCerts */,
                true /* showUsePreinstalledCertOption */);
        loadCertificates(
                mEapUserCertSpinner,
                androidKeystoreAliasLoader.getKeyCertAliases(),
                mDoNotProvideEapUserCertString,
                false /* showMultipleCerts */,
                false /* showUsePreinstalledCertOption */);

        setSelection(mEapCaCertSpinner, mUseSystemCertsString);
        mEapCaCertSpinner.setEnabled(true);
    }

    private AndroidKeystoreAliasLoader getAndroidKeystoreAliasLoader() {
        return new AndroidKeystoreAliasLoader(KeyProperties.NAMESPACE_WIFI);
    }
    private void loadCertificates(
             Spinner spinner,
             Collection<String> choices,
             String noCertificateString,
             boolean showMultipleCerts,
             boolean showUsePreinstalledCertOption) {

         ArrayList<String> certs = new ArrayList<String>();
         certs.add(mUnspecifiedCertString);
         if (showMultipleCerts) {
             certs.add(mMultipleCertSetString);
         }
         if (showUsePreinstalledCertOption) {
             certs.add(mUseSystemCertsString);
             certs.add(mInstallCertsString);
         }

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

         if (!TextUtils.isEmpty(noCertificateString)
                 && mWifiEntry.getSecurity() != WifiEntry.SECURITY_EAP_SUITE_B) {
             certs.add(noCertificateString);
         }

         // If there is only mUnspecifiedCertString and one item to select, only shows the item
         if (certs.size() == 2) {
             certs.remove(mUnspecifiedCertString);
             spinner.setEnabled(false);
         } else {
             spinner.setEnabled(true);
         }

         //final ArrayAdapter<CharSequence> adapter = getSpinnerAdapter(
                 //certs.toArray(new String[certs.size()]));
         String[] certsGroup = certs.toArray(new String[certs.size()]);
         ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                 android.R.layout.simple_spinner_item, certsGroup);
         adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
         spinner.setAdapter(adapter);
     }

     private void setSelection(Spinner spinner, String value) {
         if (value != null) {
             ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
             for (int i = adapter.getCount() - 1; i >= 0; --i) {
                 if (value.equals(adapter.getItem(i))) {
                     spinner.setSelection(i);
                     break;
                 }
             }
         }
    }

    /**
     * EAP-TLS valid fields include
     *   user_cert
     *   ca_cert
     *   domain
     *   identity
     * EAP-TTLS valid fields include
     *   phase2: PAP, MSCHAP, MSCHAPV2, GTC
     *   ca_cert
     *   identity
     *   anonymous_identity
     *   password
     */
    private void showEapFieldsByMethod(int eapMethod) {
        // Common defaults
        mView.findViewById(R.id.l_method).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.l_identity).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.l_domain).setVisibility(View.VISIBLE);

        // Defaults for most of the EAP methods and over-riden by
        // by certain EAP methods
        mView.findViewById(R.id.l_ca_cert).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.l_password_notice).setVisibility(View.VISIBLE);
        mView.findViewById(R.id.l_show_password).setVisibility(View.VISIBLE);
        switch (eapMethod) {
            case WIFI_EAP_METHOD_TLS:
                mView.findViewById(R.id.l_user_cert).setVisibility(View.VISIBLE);
                setPhase2Invisible();
                setAnonymousIdentInvisible();
                setPasswordInvisible();
                setPasswordNoticeInvisible();
                break;
            case WIFI_EAP_METHOD_TTLS:
                mView.findViewById(R.id.l_phase2).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.l_anonymous).setVisibility(View.VISIBLE);
                setUserCertInvisible();
                break;
        }

        if (mView.findViewById(R.id.l_ca_cert).getVisibility() != View.GONE) {
            String eapCertSelection = (String) mEapCaCertSpinner.getSelectedItem();
            if (eapCertSelection.equals(mUnspecifiedCertString)) {
                // Domain suffix matching is not relevant if the user hasn't chosen a CA
                // certificate yet, or chooses not to validate the EAP server.
                setDomainInvisible();
            }
        }
    }

    private void setPhase2Invisible() {
        mView.findViewById(R.id.l_phase2).setVisibility(View.GONE);
    }

    private void setAnonymousIdentInvisible() {
        mView.findViewById(R.id.l_anonymous).setVisibility(View.GONE);
    }

    private void setPasswordInvisible() {
        mView.findViewById(R.id.l_show_password).setVisibility(View.GONE);
    }

    private void setDomainInvisible() {
        mView.findViewById(R.id.l_domain).setVisibility(View.GONE);
    }

    private void setPasswordNoticeInvisible() {
        mView.findViewById(R.id.l_password_notice).setVisibility(View.GONE);
    }

    private void setUserCertInvisible() {
        mView.findViewById(R.id.l_user_cert).setVisibility(View.GONE);
        setSelection(mEapUserCertSpinner, mUnspecifiedCertString);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mEapMethodSpinner) {
            //refresh eap method and relative parameter
            Log.d(TAG, "EAP method changed, show relative parameter");
            showEapFieldsByMethod(mEapMethodSpinner.getSelectedItemPosition());
        } else if (parent == mEapCaCertSpinner) {
            //refresh ca certificate
            Log.d(TAG, "Ca Certificate spinnect clicked");
            String selectedItem = parent.getItemAtPosition(position).toString();
            if (selectedItem.equals(mInstallCertsString)) {
                startActivityForInstallCerts();
            }
            showEapFieldsByMethod(mEapMethodSpinner.getSelectedItemPosition());
        }
    }

    /**
     * Start the install page for user to install the existing certificate.
     */
    private void startActivityForInstallCerts() {
        Intent intent = new Intent(ACTION_INSTALL_CERTS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(new ComponentName(PACKAGE_INSTALL_CERTS, CLASS_INSTALL_CERTS));
        intent.putExtra(KEY_INSTALL_CERTIFICATE, INSTALL_CERTIFICATE_VALUE);

        getContext().startActivity(intent);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        //Do nothing
    }
}

