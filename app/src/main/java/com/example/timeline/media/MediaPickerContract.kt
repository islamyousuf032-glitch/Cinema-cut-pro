package com.example.timeline.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

object MediaPickerContract {
    val OpenMultiple = object : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun createIntent(context: Context, input: Array<String>): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, input)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
            val uris = mutableListOf<Uri>()
            if (resultCode == Activity.RESULT_OK && intent != null) {
                if (intent.clipData != null) {
                    val count = intent.clipData!!.itemCount
                    for (i in 0 until count) {
                        uris.add(intent.clipData!!.getItemAt(i).uri)
                    }
                } else if (intent.data != null) {
                    uris.add(intent.data!!)
                }
            }
            return uris
        }
    }
}
