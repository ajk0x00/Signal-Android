package org.thoughtcrime.securesms.stories.drive

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.signal.core.util.logging.Log
import java.io.File

@Serializable
data class SavedStoryDatabaseModel(
  val version: Int = 1,
  val lastSyncTimestamp: Long = 0,
  val savedStories: List<SavedStoryRecord> = emptyList()
)

class SavedStoryDatabase(private val context: Context) {

  companion object {
    private val TAG = Log.tag(SavedStoryDatabase::class.java)
    private const val FILE_NAME = "saved_stories_db.json"

    private val json = Json {
      prettyPrint = false
      ignoreUnknownKeys = true
    }
  }

  private val file: File
    get() = File(context.filesDir, FILE_NAME)

  private fun readModel(): SavedStoryDatabaseModel {
    if (!file.exists()) {
      return SavedStoryDatabaseModel()
    }
    return try {
      json.decodeFromString<SavedStoryDatabaseModel>(file.readText())
    } catch (e: Exception) {
      Log.w(TAG, "Corrupt DB file, starting fresh", e)
      file.delete()
      SavedStoryDatabaseModel()
    }
  }

  private fun writeModel(model: SavedStoryDatabaseModel) {
    file.writeText(json.encodeToString(SavedStoryDatabaseModel.serializer(), model))
  }

  fun getAll(): List<SavedStoryRecord> {
    return readModel().savedStories
  }

  fun add(record: SavedStoryRecord) {
    val model = readModel()
    writeModel(model.copy(savedStories = model.savedStories + record))
  }

  fun remove(driveFileId: String) {
    val model = readModel()
    writeModel(model.copy(savedStories = model.savedStories.filter { it.driveFileId != driveFileId }))
  }

  fun replaceAll(stories: List<SavedStoryRecord>) {
    val model = readModel()
    writeModel(model.copy(savedStories = stories))
  }

  fun getCount(): Int {
    return readModel().savedStories.size
  }

  fun getLastSyncTimestamp(): Long {
    return readModel().lastSyncTimestamp
  }

  fun setLastSyncTimestamp(timestamp: Long) {
    val model = readModel()
    writeModel(model.copy(lastSyncTimestamp = timestamp))
  }

  fun exists(): Boolean {
    return file.exists()
  }

  fun getJsonContent(): String {
    return json.encodeToString(SavedStoryDatabaseModel.serializer(), readModel())
  }

  fun replaceFromJson(jsonContent: String) {
    val model = json.decodeFromString<SavedStoryDatabaseModel>(jsonContent)
    writeModel(model)
  }
}
