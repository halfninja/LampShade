package com.kuxhausen.huemore.persistence;

import com.google.gson.Gson;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.support.v7.preference.PreferenceManager;
import android.provider.BaseColumns;

import com.kuxhausen.huemore.CommunityDialogFragment;
import com.kuxhausen.huemore.DisableDozeDialogFragment;
import com.kuxhausen.huemore.Helpers;
import com.kuxhausen.huemore.NetworkManagedActivity;
import com.kuxhausen.huemore.R;
import com.kuxhausen.huemore.net.NewConnectionFragment;
import com.kuxhausen.huemore.net.hue.HubData;
import com.kuxhausen.huemore.onboarding.WelcomeDialogFragment;
import com.kuxhausen.huemore.persistence.Definitions.DeprecatedPreferenceKeys;
import com.kuxhausen.huemore.persistence.Definitions.InternalArguments;
import com.kuxhausen.huemore.persistence.Definitions.NetBulbColumns;
import com.kuxhausen.huemore.persistence.Definitions.NetConnectionColumns;
import com.kuxhausen.huemore.persistence.Definitions.PreferenceKeys;

public class PreferenceInitializer {


  public static void initializedPreferencesAndShowDialogs(NetworkManagedActivity act) {
    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(act);
    Gson gson = new Gson();

    if (settings.contains(DeprecatedPreferenceKeys.BRIDGE_IP_ADDRESS)) {
      HubData hubData = new HubData();
      hubData.localHubAddress =
          settings.getString(DeprecatedPreferenceKeys.LOCAL_BRIDGE_IP_ADDRESS, null);
      hubData.portForwardedAddress =
          settings.getString(DeprecatedPreferenceKeys.INTERNET_BRIDGE_IP_ADDRESS, null);
      hubData.hashedUsername = settings.getString(DeprecatedPreferenceKeys.HASHED_USERNAME, null);
      if (hubData.localHubAddress == null) {
        hubData.localHubAddress =
            settings.getString(DeprecatedPreferenceKeys.BRIDGE_IP_ADDRESS, null);
      }

      if (hubData.hashedUsername != null
          && (hubData.localHubAddress != null || hubData.portForwardedAddress != null)) {
        ContentValues connectionValues = new ContentValues();
        connectionValues.put(Definitions.NetConnectionColumns.TYPE_COLUMN,
                             Definitions.NetBulbColumns.NetBulbType.PHILIPS_HUE);
        connectionValues.put(Definitions.NetConnectionColumns.JSON_COLUMN,
                             gson.toJson(hubData));
        long connectionId =
            Long.parseLong(act.getContentResolver()
                               .insert(Definitions.NetConnectionColumns.URI,
                                       connectionValues)
                               .getLastPathSegment());

        // also update any migrated NetBulbs to point to this NetConnection
        ContentValues bulbValues = new ContentValues();
        bulbValues.put(NetBulbColumns.CONNECTION_DATABASE_ID, connectionId);
        act.getContentResolver().update(NetBulbColumns.URI, bulbValues, null, null);
      } else {
        // remove any NetBulbs because there is no NetConnection
        act.getContentResolver().delete(NetBulbColumns.URI, null, null);
      }

      Editor edit = settings.edit();
      edit.remove(DeprecatedPreferenceKeys.LOCAL_BRIDGE_IP_ADDRESS);
      edit.remove(DeprecatedPreferenceKeys.INTERNET_BRIDGE_IP_ADDRESS);
      edit.remove(DeprecatedPreferenceKeys.BRIDGE_IP_ADDRESS);
      edit.remove(DeprecatedPreferenceKeys.HASHED_USERNAME);
      edit.commit();
    }

    if (!settings.contains(PreferenceKeys.FIRST_RUN)) {
      // Mark no longer first run in preferences cache
      Editor edit = settings.edit();
      edit.putBoolean(PreferenceKeys.FIRST_RUN, false);
      edit.commit();
    } else if (settings.getInt(PreferenceKeys.VERSION_NUMBER, -1) < act.getResources().getInteger(
        R.integer.major_update_version)) {
      // TODO show any kind of update release notes, etc
    }
    if (!settings.contains(PreferenceKeys.DEFAULT_TO_GROUPS)) {
      Editor edit = settings.edit();
      edit.putBoolean(PreferenceKeys.DEFAULT_TO_GROUPS, true);
      edit.commit();
    }
    if (!settings.contains(PreferenceKeys.DEFAULT_TO_MOODS)) {
      Editor edit = settings.edit();
      edit.putBoolean(PreferenceKeys.DEFAULT_TO_MOODS, true);
      edit.commit();
    }

    DisableDozeDialogFragment.showDozeOptOutIfNeeded(act);

    // check to see if the bridge IP address is setup yet
    String[] columns = {BaseColumns._ID, NetConnectionColumns.TYPE_COLUMN};
    Cursor cursor =
        act.getContentResolver().query(NetConnectionColumns.URI, columns, null, null, null);
    if (cursor.getCount() <= 0) {
      if (!settings.contains(PreferenceKeys.DONE_WITH_WELCOME_DIALOG)) {
        WelcomeDialogFragment wdf = new WelcomeDialogFragment();
        wdf.show(act.getSupportFragmentManager(), InternalArguments.FRAG_MANAGER_DIALOG_TAG);
      } else {
        NewConnectionFragment dhdf = new NewConnectionFragment();
        dhdf.show(act.getSupportFragmentManager(), InternalArguments.FRAG_MANAGER_DIALOG_TAG);
      }
    } else if (!settings.contains(PreferenceKeys.HAS_SHOWN_COMMUNITY_DIALOG)) {
      CommunityDialogFragment cdf = new CommunityDialogFragment();
      cdf.show(act.getSupportFragmentManager(), InternalArguments.FRAG_MANAGER_DIALOG_TAG);

    }
    cursor.close();

    if (!settings.contains(PreferenceKeys.NUMBER_OF_CONNECTED_BULBS)) {
      Editor edit = settings.edit();
      edit.putInt(PreferenceKeys.NUMBER_OF_CONNECTED_BULBS, 1);
      edit.commit();
    }

    Editor edit = settings.edit();
    try {
      edit.putInt(PreferenceKeys.VERSION_NUMBER,
                  act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionCode);
    } catch (NameNotFoundException e) {
    }
    edit.commit();

    // Performance optimization, don't re-run setDefaultValues on release builds
    boolean reRunSetDefaultValues = Helpers.isDebugVersion();
    PreferenceManager.setDefaultValues(act, R.xml.settings, reRunSetDefaultValues);
  }
}
