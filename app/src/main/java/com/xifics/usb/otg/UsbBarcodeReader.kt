package com.xifics.usb.otg

import android.content.Context

/**
* Created by Xifics on 2018/03/04.
*/
open class UsbBarcodeReader(context: Context) : UsbHIDReader(context) {

    interface UsbBarcodeCallback: UsbHostCallback {

        fun onRead(barcode: String)
    }

    private var mStringBuffer: StringBuffer = StringBuffer()

    fun openDevice(callback: UsbBarcodeCallback) {
        openDevice(null, null, callback)
    }

    /**
     * Opening USB Barcode Reader Device.
     *
     * @param vendorId
     * @param productId
     * @param callback
     * @return
     */
    private fun openDevice(vendorId: Int?, productId: Int?, callback: UsbBarcodeCallback) {
        mStringBuffer = StringBuffer()

        super.openDevice(vendorId, productId, object : UsbHostCallback {
            override fun onDeviceConnected(deviceName: String) {
                callback.onDeviceConnected(deviceName)
            }

            override fun onDeviceDisconnected() {
                callback.onDeviceDisconnected()
            }

            // barcode string data will send as only one segment or divided several segments.
            override fun onReceived(bytes: ByteArray) {
                callback.onReceived(bytes)

                if (bytes.size > 2) {
                    // get key data.
                    val keycode = bytes[2]
                    when {
                        keycode in 4..29 -> {
                            // Ascii: a(4) - z(29)
                            keycode.plus(65)
                            val ascii = byteArrayOf(keycode)
                            mStringBuffer.append(String(ascii))
                        }
                        keycode in 30..38 ->
                            // Number: 1(30) - 0(39)
                            mStringBuffer.append(keycode - 29)
                        keycode.toInt() == 39 -> // 0(39)
                            mStringBuffer.append(0)
                        keycode.toInt() == 40 -> {
                            // Enter(40) ... EOT(End of Text) signal.
                            callback.onRead(mStringBuffer.toString())
                            mStringBuffer = StringBuffer()
                        }
                    }
                }
            }

            override fun onError(message: String) {
                callback.onError(message)
            }
        })
    }
}