package com.streambert.tv.data.addons

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

object AddonNetworkUtils {

    /** First site-local IPv4 address of this device (for the phone-management URL). */
    fun localIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    /** Render [content] as a black-on-white QR bitmap of [size] px. */
    fun qrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
