package org.thebottle.libOwl.data

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Turns a folder picked with Android's folder picker into a readable path, e.g. "/sdcard/Backups/libOwl".
 * Only folders on phone storage or an SD card can be shown this way; folders from other providers
 * (Google Drive, Downloads shortcuts, etc.) return null so the caller can fall back to the folder name.
 */
fun folderDisplayPath(treeUri: String): String? = runCatching {
    val uri = Uri.parse(treeUri)
    if (uri.authority != "com.android.externalstorage.documents") return null
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val volume = docId.substringBefore(':')
    val relative = docId.substringAfter(':', "").trim('/')
    val base = if (volume == "primary") "/sdcard" else "/storage/$volume"
    if (relative.isEmpty()) base else "$base/$relative"
}.getOrNull()
