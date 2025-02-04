package com.github.jing332.tts_server_android.ui.systts.edit.bgm

import androidx.lifecycle.ViewModel
import com.github.jing332.tts_server_android.util.FileUtils
import java.io.File

class BgmTtsEditViewModel : ViewModel() {
    fun getFiles(name: String): List<File> {
        val file = File(name)
        return if (file.isFile)
            listOf(file)
        else
            FileUtils.getAllFilesInFolder(file)
                .filter { FileUtils.getMimeType(it)?.startsWith("audio") == true }
    }
}