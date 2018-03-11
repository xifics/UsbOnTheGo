package com.xifics.usb.otg

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    // TODO JAN Barcode Only?
    companion object {
        const val BARCODE_LENGTH = 13
    }

    private var mUsbReader: UsbBarcodeReader? = null

    private val mCallback = object : UsbBarcodeReader.UsbBarcodeCallback {
        override fun onDeviceConnected(deviceName: String) {
            Log.d("DEBUG", "device opened. device name: $deviceName")
        }

        override fun onDeviceDisconnected() {
            Log.d("DEBUG", "device disconnected.")
        }

        override fun onRead(barcode: String) {
            Log.d("barcode", barcode)
            if (barcode.length >= BARCODE_LENGTH) {
                Log.d("DEBUG", "onRead. barcode: $barcode")

                val snackbar = Snackbar.make(view_root, barcode, Snackbar.LENGTH_INDEFINITE)
                snackbar.setAction(android.R.string.ok) {
                    snackbar.dismiss()
                }
                snackbar.show()
            }
        }

        override fun onReceived(bytes: ByteArray) {
        }

        override fun onError(message: String) {
            Log.d("DEBUG", "on error, message: $message")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // USB Data Connection Instance.
        mUsbReader = UsbBarcodeReader(this)
        mUsbReader?.openDevice(mCallback)
    }
}
