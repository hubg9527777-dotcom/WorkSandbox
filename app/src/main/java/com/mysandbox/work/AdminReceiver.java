package com.mysandbox.work;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * 工作资料的 Profile Owner 组件。
 * 不施加密码、应用、网络、日志或跨资料策略；仅在系统完成创建后启用工作资料。
 */
public class AdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "WorkSandboxAdmin";

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = getWho(context);
        if (dpm == null) {
            Log.e(TAG, "DevicePolicyManager is unavailable after provisioning");
            return;
        }

        try {
            dpm.setProfileEnabled(admin);
            Toast.makeText(context, context.getString(R.string.toast_profile_ready),
                    Toast.LENGTH_LONG).show();
        } catch (SecurityException | IllegalArgumentException e) {
            // 不记录任何用户数据；仅记录系统策略调用失败，避免广播接收器异常退出。
            Log.e(TAG, "Unable to enable managed profile after provisioning", e);
        }
    }
}
