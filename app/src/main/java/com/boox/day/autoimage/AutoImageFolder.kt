package com.boox.day.autoimage

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Where the auto-saved images go (Nov 2026): Pictures/BooxDay/Auto by default, where galleries and
 * wallpaper pickers find them, or a folder the user picked through Android's folder picker. Files keep
 * fixed names and are replaced in place, so whatever points at one always shows the latest.
 * The picked folder is kept on this tablet only (its own preferences, not the exported settings).
 */
object AutoImageFolder {
    private const val PREFS = "auto_images_folder"
    private const val KEY_TREE = "tree_uri"
    private const val DEFAULT_LABEL = "Pictures/BooxDay/Auto"
    private const val MIME_PNG = "image/png"

    fun defaultFolder(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BooxDay/Auto")

    fun chosenFolder(context: Context): Uri? = prefs(context).getString(KEY_TREE, null)?.let(Uri::parse)

    /** Keeps the folder the picker returned, with lasting read and write access to it. */
    fun chooseFolder(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        chosenFolder(context)?.takeIf { it != tree }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        prefs(context).edit().putString(KEY_TREE, tree.toString()).apply()
    }

    fun useDefaultFolder(context: Context) {
        chosenFolder(context)?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs(context).edit().remove(KEY_TREE).apply()
    }

    /** "Pictures/BooxDay/Auto" or the picked folder's name, for Settings. */
    fun folderLabel(context: Context): String {
        val tree = chosenFolder(context) ?: return DEFAULT_LABEL
        return runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifEmpty { "Chosen folder" } }
            .getOrDefault("Chosen folder")
    }

    /** Replaces [fileName] with [png]. Blocking; call from a background thread. */
    fun write(context: Context, fileName: String, png: ByteArray) {
        val tree = chosenFolder(context)
        if (tree == null) {
            val dir = defaultFolder().apply { mkdirs() }
            val tmp = File(dir, "$fileName.tmp")
            tmp.writeBytes(png)
            val target = File(dir, fileName)
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Could not save ${target.absolutePath}" }
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME_PNG), null)
            return
        }
        val resolver = context.contentResolver
        val target = findChild(context, tree, fileName)
            ?: DocumentsContract.createDocument(resolver, rootDocument(tree), MIME_PNG, fileName)
            ?: error("The folder refused a new file")
        resolver.openOutputStream(target, "wt")?.use { it.write(png) } ?: error("Could not write $fileName")
    }

    /** Deletes the files whose names pass [isOurs] and aren't in [keep]. Blocking. */
    fun deleteOthers(context: Context, keep: Set<String>, isOurs: (String) -> Boolean) {
        val tree = chosenFolder(context)
        if (tree == null) {
            defaultFolder().listFiles { f -> f.isFile && isOurs(f.name) && f.name !in keep }?.forEach { file ->
                if (file.delete()) MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            }
            return
        }
        children(context, tree).filter { (name, _) -> isOurs(name) && name !in keep }.forEach { (_, uri) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }

    private fun rootDocument(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun findChild(context: Context, tree: Uri, name: String): Uri? =
        children(context, tree).firstOrNull { it.first == name }?.second

    private fun children(context: Context, tree: Uri): List<Pair<String, Uri>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(1) to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
            }
        }.orEmpty()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
