package com.donut.mixfile.server.core

import com.donut.mixfile.server.core.aes.encryptAES
import com.donut.mixfile.server.core.utils.bean.hashMixSHA256
import com.donut.mixfile.server.core.utils.isValidURL
import com.github.michaelbull.retry.policy.constantDelay
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.policy.stopAtAttempts
import com.github.michaelbull.retry.retry
import io.ktor.client.HttpClient

abstract class Uploader(val name: String) {

    open val referer = ""
    open val chunkSize = 1024L * 1024L

    abstract suspend fun doUpload(fileData: ByteArray, client: HttpClient): String

    companion object {
        val urlTransforms = mutableMapOf<String, (String) -> String>()
        val refererTransforms = mutableMapOf<String, (url: String, referer: String) -> String>()

        fun transformUrl(url: String): String {
            return urlTransforms.entries.fold(url) { acc, (_, transform) ->
                transform(acc)
            }
        }

        fun transformReferer(url: String, referer: String): String {
            return refererTransforms.entries.fold(referer) { acc, (_, transform) ->
                transform(url, acc)
            }
        }

        fun registerUrlTransform(name: String, transform: (String) -> String) {
            urlTransforms[name] = transform
        }

        fun registerRefererTransform(
            name: String,
            transform: (url: String, referer: String) -> String,
        ) {
            refererTransforms[name] = transform
        }
    }

    suspend fun upload(
        head: ByteArray,
        fileData: ByteArray,
        key: ByteArray,
        mixFileServer: MixFileServer
    ): String {
        val policy =
            constantDelay<Throwable>(delayMillis = 100L) + stopAtAttempts(mixFileServer.requestRetryCount)
        return retry(policy) {
            val encryptedData = encryptBytes(head, fileData, key)
            try {
                val url = doUpload(
                    encryptedData,
                    mixFileServer.httpClient
                ) + "#${fileData.hashMixSHA256()}"
                if (!isValidURL(url)) {
                    throw Exception("url格式错误")
                }
                url
            } finally {
                mixFileServer.onUploadData(encryptedData)
            }
        }
    }

    open suspend fun genHead(client: HttpClient): ByteArray? = null

    private fun encryptBytes(head: ByteArray, fileData: ByteArray, key: ByteArray): ByteArray {
        return head + (encryptAES(fileData, key))
    }

}