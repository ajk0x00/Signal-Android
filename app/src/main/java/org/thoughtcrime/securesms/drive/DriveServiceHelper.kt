package org.thoughtcrime.securesms.drive

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import org.signal.core.util.logging.Log
import java.io.InputStream
import java.io.OutputStream

class DriveServiceHelper(private val drive: Drive) {

  companion object {
    private val TAG = Log.tag(DriveServiceHelper::class.java)
    private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    private const val DB_FILE_NAME = "saved_stories_db.json"
  }

  suspend fun ensureFolderExists(folderName: String): String {
    val query = "mimeType='$FOLDER_MIME_TYPE' and name='$folderName' and trashed=false"
    val result: FileList = drive.files().list()
      .setQ(query)
      .setSpaces("drive")
      .setFields("files(id)")
      .execute()

    if (result.files.isNotEmpty()) {
      return result.files[0].id
    }

    val metadata = File()
      .setName(folderName)
      .setMimeType(FOLDER_MIME_TYPE)

    val folder = drive.files().create(metadata)
      .setFields("id")
      .execute()

    Log.i(TAG, "Created Drive folder: $folderName (${folder.id})")
    return folder.id
  }

  suspend fun uploadFile(folderId: String, fileName: String, inputStream: InputStream, mimeType: String): String {
    val metadata = File()
      .setName(fileName)
      .setParents(listOf(folderId))

    val file = drive.files().create(metadata, com.google.api.client.http.InputStreamContent(mimeType, inputStream))
      .setFields("id")
      .execute()

    Log.i(TAG, "Uploaded file: $fileName (${file.id})")
    return file.id
  }

  suspend fun downloadFile(fileId: String, outputStream: OutputStream) {
    drive.files().get(fileId)
      .executeMediaAndDownloadTo(outputStream)
  }

  suspend fun listFiles(folderId: String): List<DriveFileInfo> {
    val query = "'$folderId' in parents and trashed=false"
    val result: FileList = drive.files().list()
      .setQ(query)
      .setSpaces("drive")
      .setFields("files(id,name,mimeType,size,modifiedTime)")
      .execute()

    return result.files.map { file ->
      DriveFileInfo(
        id = file.id,
        name = file.name,
        mimeType = file.mimeType,
        size = file.size?.toLong() ?: 0L,
        modifiedTime = file.modifiedTime?.value?.toLong() ?: 0L
      )
    }
  }

  suspend fun downloadJsonDb(folderId: String): String? {
    val query = "'$folderId' in parents and name='$DB_FILE_NAME' and trashed=false"
    val result: FileList = drive.files().list()
      .setQ(query)
      .setSpaces("drive")
      .setFields("files(id)")
      .execute()

    if (result.files.isEmpty()) {
      Log.i(TAG, "No DB file found on Drive")
      return null
    }

    val fileId = result.files[0].id
    val outputStream = java.io.ByteArrayOutputStream()
    downloadFile(fileId, outputStream)
    return outputStream.toString("UTF-8")
  }

  suspend fun uploadJsonDb(folderId: String, jsonContent: String) {
    val query = "'$folderId' in parents and name='$DB_FILE_NAME' and trashed=false"
    val result: FileList = drive.files().list()
      .setQ(query)
      .setSpaces("drive")
      .setFields("files(id)")
      .execute()

    val bytes = jsonContent.toByteArray(Charsets.UTF_8)
    val inputStream = bytes.inputStream()
    val mediaContent = com.google.api.client.http.InputStreamContent("application/json", inputStream)

    if (result.files.isNotEmpty()) {
      val fileId = result.files[0].id
      drive.files().update(fileId, File(), mediaContent)
        .execute()
      Log.i(TAG, "Updated DB file on Drive: $fileId")
    } else {
      val metadata = File()
        .setName(DB_FILE_NAME)
        .setParents(listOf(folderId))
      drive.files().create(metadata, mediaContent)
        .setFields("id")
        .execute()
      Log.i(TAG, "Created DB file on Drive")
    }
  }
}
