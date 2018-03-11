package com.xifics.usb.otg

/**
* Created by Xifics on 2018/03/04.
*/
interface UsbHostCallback {

    fun onDeviceConnected(deviceName: String)
    fun onDeviceDisconnected()
    fun onReceived(bytes: ByteArray)
    fun onError(message: String)
}