package org.thoughtcrime.securesms.stories.cloudstorage

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies

class CloudStorageSyncScheduler {

  companion object {
    private val TAG = Log.tag(CloudStorageSyncScheduler::class.java)
  }

  fun scheduleIfNecessary() {
    try {
      AppDependencies.application.assets.open("cloud_storage_config.json").close()
    } catch (e: Exception) {
      return
    }
    CloudStorageSyncJob.enqueueIfNecessary()
  }
}
