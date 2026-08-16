/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util.AppIconPreset
import org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util.AppIconUtility
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Manages dynamically switching the app launcher icon based on whether there
 * are unread stories. Switches to the COLOR preset when unread stories exist,
 * and reverts to DEFAULT when all stories have been viewed.
 */
object StoryAppIconManager {

  private val TAG = Log.tag(StoryAppIconManager::class.java)

  @JvmStatic
  fun update(context: Context = AppDependencies.application) {
    val appContext = context.applicationContext
    SignalExecutors.BOUNDED_IO.execute {
      try {
        if (!Stories.isFeatureEnabled()) {
          return@execute
        }

        val hasUnreadStories = SignalDatabase.messages.getUnreadStoryThreadRecipientIds().isNotEmpty()
        val appIconUtility = AppIconUtility(appContext)
        val currentPreset = appIconUtility.currentAppIcon

        val desiredPreset = if (hasUnreadStories) {
          AppIconPreset.COLOR
        } else {
          AppIconPreset.DEFAULT
        }

        // Only switch if needed and only toggle between DEFAULT and COLOR
        if (currentPreset != desiredPreset && (currentPreset == AppIconPreset.DEFAULT || currentPreset == AppIconPreset.COLOR)) {
          Log.i(TAG, "Switching app icon from ${currentPreset.name} to ${desiredPreset.name} (hasUnreadStories=$hasUnreadStories)")
          appIconUtility.setNewAppIcon(desiredPreset)
        }
      } catch (t: Throwable) {
        Log.w(TAG, "Failed to update story app icon", t)
      }
    }
  }
}
