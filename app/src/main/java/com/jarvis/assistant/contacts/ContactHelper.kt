package com.jarvis.assistant.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

object ContactHelper {

    private const val TAG = "JarvisContactHelper"

    /**
     * Resolves incoming phone number to saved Contact Name via PhoneLookup
     */
    fun resolveContactName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CONTACTS permission not granted.")
            return null
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        var contactName: String? = null
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex != -1) {
                    contactName = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact name for $phoneNumber", e)
        } finally {
            cursor?.close()
        }

        return contactName
    }

    /**
     * Resolves display name and phone number from picked contact URI
     */
    fun getContactDetailsFromUri(context: Context, contactUri: Uri): Pair<String?, String?> {
        var displayName: String? = null
        var phoneNumber: String? = null

        try {
            // First attempt: try querying as CommonDataKinds.Phone
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(contactUri, phoneProjection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                    if (numIdx != -1) phoneNumber = cursor.getString(numIdx)
                }
            }

            // Second attempt: if displayName is still null, query ContactsContract.Contacts
            if (displayName == null) {
                val contactProjection = arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                )
                context.contentResolver.query(contactUri, contactProjection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                        if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                        val contactId = if (idIdx != -1) cursor.getString(idIdx) else null
                        val hasPhone = if (hasPhoneIdx != -1) cursor.getInt(hasPhoneIdx) > 0 else false

                        if (hasPhone && contactId != null) {
                            context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
                            )?.use { phoneCursor ->
                                if (phoneCursor.moveToFirst()) {
                                    val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numIdx != -1) phoneNumber = phoneCursor.getString(numIdx)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact details from URI: $contactUri", e)
        }

        return Pair(displayName, phoneNumber)
    }

    /**
     * Queries Android Contacts to find phone number for a given name
     */
    fun getPhoneNumberForContact(context: Context, contactName: String): String? {
        if (contactName.isBlank()) return null

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CONTACTS permission not granted.")
            return null
        }

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$contactName%")

        var phoneNumber: String? = null
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
            )
            if (cursor != null && cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    phoneNumber = cursor.getString(numberIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying phone number for $contactName", e)
        } finally {
            cursor?.close()
        }

        return phoneNumber
    }

    /**
     * Dispatches ACTION_CALL intent directly to the Android telephony stack
     */
    fun makePhoneCall(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "CALL_PHONE permission not granted.")
            return false
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating phone call to $phoneNumber", e)
            false
        }
    }
}
