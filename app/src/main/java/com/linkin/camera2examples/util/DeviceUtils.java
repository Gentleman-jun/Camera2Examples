package com.linkin.camera2examples.util;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Author: Linkin
 * Time：2018/8/27
 * Email：liuzhongjun@novel-supertv.com
 * Blog：https://blog.csdn.net/Android_Technology
 * Desc: 设备相关工具类
 */

public final class DeviceUtils {

    /**
     * 检验设备是否有摄像头
     */
    public static boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }
}
