package com.callguard.app.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * ContactsHelper — Phase 2 contact whitelist lookup.
 *
 * Checks whether an incoming caller number exists in the device's
 * contacts list. Known contacts are automatically whitelisted —
 * their calls skip spam detection and are answered normally.
 *
 * Requires {@code READ_CONTACTS} permission (declared in manifest and
 * requested at runtime before use).
 *
 * All lookups are synchronous and should be called from a background thread.
 */
public class ContactsHelper {

    private static final String TAG = "CG_Contacts";

    // Private constructor — utility class
    private ContactsHelper() {}

    /**
     * Checks if the given phone number belongs to a saved contact.
     *
     * @param context      Application context (needs READ_CONTACTS permission).
     * @param phoneNumber  The raw phone number to look up.
     * @return {@code true} if the number is in the device contacts, {@code false} otherwise.
     */
    public static boolean isKnownContact(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()
                || "unknown".equalsIgnoreCase(phoneNumber)) {
            return false;
        }

        try {
            Uri lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber)
            );

            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(
                    lookupUri,
                    new String[]{ ContactsContract.PhoneLookup.DISPLAY_NAME },
                    null, null, null
            );

            if (cursor != null) {
                boolean found = cursor.moveToFirst();
                if (found) {
                    String name = cursor.getString(0);
                    Log.d(TAG, "Number " + phoneNumber + " belongs to contact: " + name);
                }
                cursor.close();
                return found;
            }
        } catch (SecurityException e) {
            // READ_CONTACTS permission not granted — treat as unknown
            Log.w(TAG, "READ_CONTACTS permission denied: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error looking up contact: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Returns the display name for a phone number, or null if not found.
     *
     * @param context      Application context.
     * @param phoneNumber  The raw phone number to look up.
     * @return Display name string, or {@code null} if not a known contact.
     */
    public static String getContactName(Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return null;

        try {
            Uri lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber)
            );

            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(
                    lookupUri,
                    new String[]{ ContactsContract.PhoneLookup.DISPLAY_NAME },
                    null, null, null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                cursor.close();
                return name;
            }
            if (cursor != null) cursor.close();
        } catch (SecurityException e) {
            Log.w(TAG, "READ_CONTACTS permission denied.");
        } catch (Exception e) {
            Log.e(TAG, "Error fetching contact name: " + e.getMessage(), e);
        }

        return null;
    }
}
