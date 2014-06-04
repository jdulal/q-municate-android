package com.quickblox.qmunicate.core.gcm;

import android.app.Activity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.quickblox.module.users.model.QBUser;
import com.quickblox.qmunicate.App;
import com.quickblox.qmunicate.core.gcm.tasks.QBGCMRegistrationTask;
import com.quickblox.qmunicate.utils.Consts;
import com.quickblox.qmunicate.utils.PrefsHelper;
import com.quickblox.qmunicate.utils.Utils;

public class GSMHelper {

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private final static String TAG = GSMHelper.class.getSimpleName();
    private GoogleCloudMessaging gcm;
    private Activity activity;

    public GSMHelper(Activity activity) {
        this.activity = activity;
        gcm = GoogleCloudMessaging.getInstance(activity);
    }

    public boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, activity, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            }
            return false;
        }
        return true;
    }

    public void registerInBackground() {
        new QBGCMRegistrationTask(activity).execute(gcm);
    }

    public int getSubscriptionId() {
        PrefsHelper prefsHelper = App.getInstance().getPrefsHelper();
        int subscriptionId = prefsHelper.getPref(PrefsHelper.PREF_SUBSCRIPTION_ID,
                Consts.NOT_INITIALIZED_VALUE);
        return subscriptionId;
    }

    public String getRegistrationId() {
        PrefsHelper prefsHelper = App.getInstance().getPrefsHelper();
        String registrationId = prefsHelper.getPref(PrefsHelper.PREF_REG_ID, Consts.EMPTY_STRING);
        return registrationId;
    }

    public boolean isDeviceRegisteredWithUser(QBUser user) {
        PrefsHelper prefsHelper = App.getInstance().getPrefsHelper();
        String registrationId = prefsHelper.getPref(PrefsHelper.PREF_REG_ID, Consts.EMPTY_STRING);
        int registeredUserId = prefsHelper.getPref(PrefsHelper.PREF_REG_USER_ID,
                Consts.NOT_INITIALIZED_VALUE);

        int registeredVersion = prefsHelper.getPref(PrefsHelper.PREF_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = Utils.getAppVersionCode(activity);

        return (!registrationId.isEmpty()) && (Consts.NOT_INITIALIZED_VALUE != registeredUserId) &&
                (registeredVersion == currentVersion);
    }

    //TODO VF will be defined throw core qb
    public void subscribeToPushNotifications() {
        new QBGCMRegistrationTask(activity).execute(gcm);
    }
}
