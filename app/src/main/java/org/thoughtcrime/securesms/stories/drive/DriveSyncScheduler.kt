package org.thoughtcrime.securesms.stories.drive

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies

class DriveSyncScheduler {

  companion object {
    private val TAG = Log.tag(DriveSyncScheduler::class.java)
  }

  fun scheduleIfNecessary() {
    try {
      AppDependencies.application.assets.open("drive_credentials.json").close()
    } catch (e: Exception) {
      return
    }
    DriveSyncJob.enqueueIfNecessary()
  }
}
