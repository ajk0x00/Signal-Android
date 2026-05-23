package org.thoughtcrime.securesms.drive

import android.content.Context
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.drive.DriveScopes
import org.signal.core.util.logging.Log

object DriveCredentialsProvider {

  private val TAG = Log.tag(DriveCredentialsProvider::class.java)

  fun createCredential(context: Context): GoogleCredential? {
    try {
      val stream = context.assets.open("drive_credentials.json")
      val credential = GoogleCredential.fromStream(stream)
        .createScoped(listOf(DriveScopes.DRIVE_FILE))
      return credential
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load Drive credentials", e)
      return null
    }
  }
}
