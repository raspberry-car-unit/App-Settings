/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.datausage;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.loader.content.AsyncTaskLoader;

/**
 * Fetches the network stats using the {@link NetworkStatsManager}.
 *
 * <p>Class is taken from {@link com.android.settingslib.net.SummaryForAllUidLoader}. The only
 * difference is we are using {@link AsyncTaskLoader} instead of {@link
 * android.content.AsyncTaskLoader}.
 */
public class SummaryForAllUidLoader extends AsyncTaskLoader<NetworkStats> {
    private static final String KEY_SUBSCRIBER_ID = "subscriber_id";
    private static final String KEY_START = "start";
    private static final String KEY_END = "end";

    private final Bundle mArgs;

    /**
     * Builds the bundle given the template, start and end.
     */
    public static Bundle buildArgs(NetworkTemplate template, long start, long end) {
        Bundle args = new Bundle();
        args.putString(KEY_SUBSCRIBER_ID, template.getSubscriberId());
        args.putLong(KEY_START, start);
        args.putLong(KEY_END, end);
        return args;
    }

    public SummaryForAllUidLoader(Context context, Bundle args) {
        super(context);
        mArgs = args;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

    @Override
    public NetworkStats loadInBackground() {
        final String subscriberId = mArgs.getString(KEY_SUBSCRIBER_ID);
        final long start = mArgs.getLong(KEY_START);
        final long end = mArgs.getLong(KEY_END);

        try {
            final NetworkStatsManager statsManager =
                    getContext().getSystemService(NetworkStatsManager.class);
            return statsManager.querySummary(ConnectivityManager.TYPE_MOBILE, subscriberId, start,
                    end);
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();
        cancelLoad();
    }
}
