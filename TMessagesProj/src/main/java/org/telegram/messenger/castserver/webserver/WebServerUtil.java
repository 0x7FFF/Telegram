package org.telegram.messenger.castserver.webserver;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WebServerUtil {
    private WebServerUtil() {}

    public static String findIPAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        try {
            if (wifiManager.getConnectionInfo() != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                return InetAddress.getByAddress(
                        ByteBuffer.allocate(4)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(wifiInfo.getIpAddress())
                                .array()
                ).getHostAddress();
            }
            return null;
        } catch (Exception e) {
            Log.e(WebServerUtil.class.getName(), "Error finding IpAddress: " + e.getMessage(), e);
            return null;
        }
    }
}
