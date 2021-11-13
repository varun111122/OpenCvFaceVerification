package com.example.oponcvdemo

import android.content.Context
import androidx.annotation.RawRes
import java.io.*
import java.lang.Exception

object FileUtils {
    fun copyFileFromRawToOthers(context: Context, @RawRes id: Int, targetPath: String) {
        val `in` = context.resources.openRawResource(id)
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(targetPath)
            val buff = ByteArray(1024)
            var read = 0
            while (`in`.read(buff).also { read = it } > 0) {
                out.write(buff, 0, read)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (`in` != null) {
                    `in`.close()
                }
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun copyFile(srcPath: String?, targetPath: String?) {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = FileInputStream(srcPath)
            out = FileOutputStream(targetPath)
            val buf = ByteArray(1024)
            var len: Int
            while (`in`.read(buf).also { len = it } > 0) {
                out.write(buf, 0, len)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                `in`?.close()
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
