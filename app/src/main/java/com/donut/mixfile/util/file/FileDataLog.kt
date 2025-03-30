package com.donut.mixfile.util.file

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alibaba.fastjson2.annotation.JSONField
import com.donut.mixfile.server.core.utils.bean.MixShareInfo
import com.donut.mixfile.server.core.utils.resolveMixShareInfo
import com.donut.mixfile.ui.component.common.MixDialogBuilder
import com.donut.mixfile.ui.routes.autoAddFavorite
import com.donut.mixfile.ui.routes.favorites.currentCategory
import com.donut.mixfile.ui.routes.home.getLocalServerAddress
import com.donut.mixfile.util.cachedMutableOf
import com.donut.mixfile.util.getFileAccessUrl
import com.donut.mixfile.util.showToast
import java.util.Date


data class FileDataLog(
    val shareInfoData: String,
    val name: String,
    val size: Long,
    val time: Date = Date(),
    var category: String = currentCategory.ifEmpty { "默认" },
) {

    init {
        //限制category长度
        if (category.length > 20) {
            category = category.substring(0, 20)
        }
        category = category.trim()
    }

    @get:JSONField(serialize = false)
    val downloadUrl: String
        get() = getFileAccessUrl(getLocalServerAddress(), shareInfoData, name)

    fun updateDataList(
        list: List<FileDataLog>,
        action: (FileDataLog) -> FileDataLog
    ): List<FileDataLog> {
        return list.map {
            if (it.shareInfoData == this.shareInfoData) {
                action(it)
            } else {
                it
            }
        }
    }

    fun rename(callback: (FileDataLog) -> Unit = {}) {
        var shareInfo = resolveMixShareInfo(shareInfoData) ?: return
        MixDialogBuilder("重命名文件").apply {
            var name by mutableStateOf(shareInfo.fileName)
            setContent {
                OutlinedTextField(value = name, onValueChange = {
                    name = it
                }, modifier = Modifier.fillMaxWidth(), label = {
                    Text(text = "输入文件名")
                })
            }
            setDefaultNegative()
            setPositiveButton("确定") {
                if (name.isEmpty()) {
                    showToast("文件名不能为空!")
                    return@setPositiveButton
                }
                shareInfo = shareInfo.copy(fileName = name)
                val renamedLog = copy(
                    name = name,
                    shareInfoData = shareInfo.toString()
                )
                favorites = updateDataList(favorites) {
                    renamedLog
                }
                uploadLogs = updateDataList(uploadLogs) {
                    renamedLog
                }
                callback(renamedLog)
                showToast("重命名文件成功!")
                closeDialog()
            }
            show()
        }
    }

    override fun hashCode(): Int {
        var result = shareInfoData.hashCode()
        result = 31 * result + category.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FileDataLog) return false
        return shareInfoData.contentEquals(other.shareInfoData) && category.contentEquals(other.category)
    }
}


var favorites by cachedMutableOf(listOf<FileDataLog>(), "favorite_file_logs")

var uploadLogs by cachedMutableOf(listOf<FileDataLog>(), "upload_file_logs")

var favCategories by cachedMutableOf(setOf("默认"), "fav_categories")

fun addUploadLog(shareInfo: MixShareInfo) {
    if (autoAddFavorite) {
        addFavoriteLog(shareInfo.toDataLog())
    }
    val uploadLog = shareInfo.toDataLog()
    if (uploadLogs.size > 1000) {
        uploadLogs = uploadLogs.drop(1)
    }
    uploadLogs = uploadLogs + uploadLog
}


fun deleteUploadLog(uploadLog: FileDataLog, callback: () -> Unit = {}) {
    MixDialogBuilder("确定删除?").apply {
        setContent {
            Text(text = "确定从上传记录中删除?")
        }
        setPositiveButton("确定") {
            uploadLogs = uploadLogs.filter { it != uploadLog }
            closeDialog()
            callback()
            showToast("删除成功")
        }
        setDefaultNegative()
        show()
    }
}


fun addFavoriteLog(
    log: FileDataLog,
    category: String = currentCategory.ifEmpty { "默认" },
): Boolean {
    favCategories += category
    favorites = favorites.filter { it.shareInfoData != log.shareInfoData }
    favorites = favorites + log.copy(category = category)
    return true
}

fun MixShareInfo.toDataLog(): FileDataLog {
    return FileDataLog(
        shareInfoData = this.toString(),
        name = this.fileName,
        size = this.fileSize
    )
}

fun deleteFavoriteLog(uploadLog: FileDataLog, callback: () -> Unit = {}) {
    MixDialogBuilder("确定删除?").apply {
        setContent {
            Text(text = "确定从收藏记录中删除?")
        }
        setPositiveButton("确定") {
            favorites -= uploadLog
            closeDialog()
            callback()
            showToast("删除成功")
        }
        setDefaultNegative()
        show()
    }
}
