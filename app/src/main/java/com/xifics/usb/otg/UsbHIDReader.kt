package com.xifics.usb.otg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Handler
import android.util.Log

/**
* Created by Xifics on 2018/03/04.
*/
open class UsbHIDReader(context: Context) {

    companion object {
        private const val PERMISSION = "com.xifics.usb.PERMISSION"
        private const val READ_INTERVAL_MILLIS = 50
        private const val TAG = "UsbHIDReader"
    }

    private val mPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PERMISSION) {
                synchronized(this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)?.let {
                            stopThread()
                            mDevice = it
                            // TODO Reconsider UsbBarcodeCallback Format
                            mCallback?.onDeviceConnected(it.productName)
                            startThread()
                        }
                        mContext.unregisterReceiver(this)
                    } else {
                        mCallback?.onError("permission denied")
                    }
                }
            }
        }
    }

    private val mAttachAndDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    synchronized(this) {
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)?.let {
                            // in case of connect a usb device after this receiver registered
                            mManager.requestPermission(mDevice, PendingIntent.getBroadcast(
                                    mContext, 0, Intent(PERMISSION), 0))
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    stopThread()
                    mCallback?.onDeviceDisconnected()
                    mContext.registerReceiver(mPermissionReceiver, IntentFilter(PERMISSION))
                }
            }
        }
    }

    private val mReader = Runnable {
        while (mConnection != null) {
            synchronized(mLock) {
                val buffer = ByteArray(mPacketSize)
                mConnection?.bulkTransfer(mEndPoint, buffer,
                        mPacketSize, READ_INTERVAL_MILLIS)?.let { length ->
                    if (length >= 0) {
                        val bytes = ByteArray(length)
                        System.arraycopy(buffer, 0, bytes, 0, length)
                        mHandler.post {
                            mCallback?.onReceived(bytes)
                        }
                    }
                }
            }
            try {
                Thread.sleep(READ_INTERVAL_MILLIS.toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private val mContext = context
    private val mHandler = Handler()

    private val mManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var mDevice: UsbDevice? = null

    private var mConnection: UsbDeviceConnection? = null
    private var mInterface: UsbInterface? = null
    private var mEndPoint: UsbEndpoint? = null
    private var mPacketSize: Int = 0

    private var mReadThread: Thread? = null
    private val mLock = Any()

    /**
     * UsbBarcodeCallback for UsbHidReader
     */
    private var mCallback: UsbHostCallback? = null

    @Suppress("unused")
    val deviceName: String?
        get() = mDevice?.productName

    @Suppress("unused")
    val isOpen: Boolean
        get() = mDevice != null

    @Suppress("unused")
    open fun openDevice(callback: UsbHostCallback) {
        openDevice(null, null, callback)
    }

    open fun openDevice(vendorId: Int? = null, productId: Int? = null, callback: UsbHostCallback) {
        mCallback = callback

        // Get UsbDevice Instance
        val deviceList = mManager.deviceList
        deviceList.forEach { _, usbDevice ->
            vendorId?.let {
                if (it != usbDevice.vendorId) return@forEach
            }
            productId?.let {
                if (it != usbDevice.productId) return@forEach
            }
            mDevice = usbDevice
            return@forEach
        }

        mDevice?.let {
            mContext.registerReceiver(mPermissionReceiver, IntentFilter(PERMISSION))
            mContext.registerReceiver(mAttachAndDetachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
            mContext.registerReceiver(mAttachAndDetachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))

            mManager.requestPermission(it, PendingIntent.getBroadcast(
                    mContext, 0, Intent(PERMISSION), 0))
        }
    }

    @Suppress("unused")
    fun closeDevice() {
        stopThread()

        try {
            mContext.unregisterReceiver(mPermissionReceiver)
            mContext.unregisterReceiver(mAttachAndDetachReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, e.message)
        }

        mCallback = null
        mDevice = null
    }

    private fun startThread() {
        mDevice?.let {
            mConnection = mManager.openDevice(it)
            mInterface = it.getInterface(0)
            mEndPoint = mInterface?.getEndpoint(0)
            mEndPoint?.let {
                mPacketSize = it.maxPacketSize
            }

            mConnection?.claimInterface(mInterface, true)
            if (mReadThread == null) {
                mReadThread = Thread(mReader)
                mReadThread?.start()
            } else {
                Log.d(TAG, "thread is already started.")
            }
        }
    }

    private fun stopThread() {
        mReadThread?.let {
            mConnection?.let { conn ->
                conn.releaseInterface(mInterface)
                conn.close()
                mConnection = null
            }
        }
    }
}