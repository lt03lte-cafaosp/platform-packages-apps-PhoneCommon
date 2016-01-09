/*
 * Copyright (C) 2015, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
 * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
          copyright notice, this list of conditions and the following
          disclaimer in the documentation and/or other materials provided
          with the distribution.
 * Neither the name of The Linux Foundation, Inc. nor the names of its
          contributors may be used to endorse or promote products derived
          from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.phone.common.util;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.widget.Toast;
import android.util.Log;

import com.android.phone.common.R;
import com.android.internal.telephony.PhoneConstants;

public class FirewallUtils {
    private static final String TAG = "FirewallUtils";
    private static final String BLOCK_CALL_INTENT = "com.android.firewall.ADD_CALL_BLOCK_RECORD";
    private static final String FIREWALL_APK_NAME = "com.android.firewall";
    private static final Uri FIREWALL_PROVIDER_URI = Uri
            .parse("content://com.android.firewall");
    private static final Uri WHITELIST_CONTENT_URI = Uri
            .parse("content://com.android.firewall/whitelistitems");
    private static final Uri BLACKLIST_CONTENT_URI = Uri
            .parse("content://com.android.firewall/blacklistitems");
    private static final int COMPARE_NUMBER_LEN = 11;
    private static final int FIREWALL_LIST_MAX_ITEM_NUM = 100;
    private static final String EXTRA_NUMBER = "phonenumber";
    private static final String IS_FORBIDDEN = "isForbidden";
    private static final String SUB_ID = "sub_id";
    private static final String BLACKLIST = "blacklist";
    private static final String WHITELIST = "whitelist";
    private static final String NAME_KEY = "name";
    private static final String NUMBER_KEY = "number";
    private static final String PERSON_KEY = "person_id";
    private static final String MODE_KEY = "mode";

    public static boolean isFireWallInstalled(Context context) {
        boolean installed = false;
        if (context != null) {
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                        FIREWALL_APK_NAME, PackageManager.GET_PROVIDERS);
                installed = (info != null);
            } catch (NameNotFoundException e) {
                // do nothing
            }
        }
        Log.d(TAG, "Is Firewall installed ? " + installed);
        return installed;
    }

    public static boolean isBlockedByFirewall(Context context, String number, int sub) {
        boolean isForbidden = false;
        if (context != null) {
            // Add to check the firewall when firewall provider is built.
            final ContentResolver cr = context.getContentResolver();
            if (cr.acquireProvider(FIREWALL_PROVIDER_URI) != null) {
                Bundle extras = new Bundle();
                extras.putInt(PhoneConstants.SUBSCRIPTION_KEY, sub);
                extras.putString(EXTRA_NUMBER, number);
                extras = cr.call(FIREWALL_PROVIDER_URI, IS_FORBIDDEN, null, extras);
                if (extras != null) {
                    isForbidden = extras.getBoolean(IS_FORBIDDEN);
                }
            }
        }
        return isForbidden;
    }

    public static void sendBlockRecordBroadcast (Context context, int subId, String number) {
        if (context != null) {
            Intent intent = new Intent(BLOCK_CALL_INTENT);
            intent.putExtra(SUB_ID, subId);
            intent.putExtra(NUMBER_KEY, number);
            context.sendBroadcast(intent);
        }
    }

    public static boolean addNumberToFirewall(Context context, String number, boolean isBlacklist) {
        if (context == null) {
            return false;
        }
        if (TextUtils.isEmpty(number)) {
            Toast.makeText(context, context.getString(R.string.firewall_number_len_not_valid),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return addToFirewall(context, isBlacklist, number, null, 0);
    }

    public static boolean isNumberInFirewall(Context context, boolean isBlacklist, String number) {
        if (context == null || TextUtils.isEmpty(number)) {
            return false;
        }
        Uri firewallUri = isBlacklist? BLACKLIST_CONTENT_URI : WHITELIST_CONTENT_URI;
        if(isNumberInFirewall(context, firewallUri, number)) {
            return true;
        }
        return false;
    }

    private static String formatFirewallNumber(String number) {
        if (number == null) {
            return null;
        }
        number = number.replaceAll("[\\-\\/ ]", "");
        return number;
    }

    public static  boolean isNumberInFirewall(Context context, Uri firewallUri, String number) {
        if (context != null) {
            String minMatchNumber = PhoneNumberUtils.toCallerIDMinMatch(number);
            String minNumber= new StringBuffer(minMatchNumber).reverse().toString();
            number = formatFirewallNumber(minNumber);
            Log.d(TAG, "minNumber = " + minNumber + " number = " + number);
            Cursor firewallCursor = null;
            try {
                firewallCursor = context.getContentResolver().query(firewallUri,
                        null, "number" + " LIKE '%" + number + "'", null, null);
                if (firewallCursor != null && firewallCursor.getCount() > 0) {
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, e);
            } finally {
                if (firewallCursor != null) {
                    firewallCursor.close();
                    firewallCursor = null;
                }
            }
        }
        return false;
    }

    public static boolean removeFromFirewall(Context context, boolean isBlacklist, String number) {
        if (context == null || TextUtils.isEmpty(number)) {
            return false;
        }
        number =  formatFirewallNumber(number);
        Uri firewallUri = isBlacklist? BLACKLIST_CONTENT_URI : WHITELIST_CONTENT_URI;
        String deleteSelection = "number=?";
        String deleteSelectionArgs [] = new String[] {
                String.valueOf(number)};
        boolean result = context.getContentResolver().delete(firewallUri, deleteSelection,
                deleteSelectionArgs) >= 0;
        if (result) {
            return true;
        }
        return false;
    }

    public static boolean addToFirewall(Context context, boolean isBlacklist, String number,
            String name, int personId) {
        if (context == null || TextUtils.isEmpty(number)) {
            return false;
        }
        number =  formatFirewallNumber(number);
        Uri firewallUri = isBlacklist? BLACKLIST_CONTENT_URI : WHITELIST_CONTENT_URI;
        if (isReachedMaxLimit(context, firewallUri)) {
            return false;
        }
        ContentValues values = new ContentValues();
        if (!TextUtils.isEmpty(name)) {
            values.put(NAME_KEY, name);
        }
        values.put(NUMBER_KEY, number);
        values.put(PERSON_KEY, personId);
        boolean result = context.getContentResolver().insert(firewallUri, values) != null;
        if (result) {
            return true;
        }
        return false;
    }

    public static boolean isReachedMaxLimit(Context context, Uri uri) {
        if (context != null) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor.getCount() >= FIREWALL_LIST_MAX_ITEM_NUM) {
                    Toast.makeText(context, R.string.firewall_reach_maximun,
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
            } finally {
                if (cursor != null && !cursor.isClosed()) {
                    cursor.close();
                }
            }
        }
        return false;
    }

    public static void tryAddingNumberIntoBlacklist(final Context context, final String number) {
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.firewall_add_blacklist_warning))
            .setPositiveButton(android.R.string.ok, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    boolean success = addNumberToFirewall(context, number, true);
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.firewall_save_success),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create().show();
    }

    public static void tryAddingNumberIntoWhitelist(final Context context, final String number) {
        if (context == null) {
            return;
        }
        boolean success = addNumberToFirewall(context, number, false);
        if (success) {
            Toast.makeText(context, context.getString(R.string.firewall_save_success),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static Intent createIntentForWhitelist(String number, String name) {
        Intent intent = new Intent();
        Bundle whiteBundle = new Bundle();
        whiteBundle.putString(NUMBER_KEY, number);
        whiteBundle.putInt(PERSON_KEY, 0);// optional
        whiteBundle.putString(MODE_KEY, WHITELIST);
        whiteBundle.putString(NAME_KEY, name);
        return intent;
    }

    public static Intent createIntentForBlacklist(String number, String name) {
        Intent intent = new Intent();
        Bundle blackBundle = new Bundle();
        blackBundle.putString(NUMBER_KEY, number);
        blackBundle.putInt(PERSON_KEY, 0);// optional
        blackBundle.putString(MODE_KEY, BLACKLIST);
        blackBundle.putString(NAME_KEY, name);
        return intent;
    }
}
