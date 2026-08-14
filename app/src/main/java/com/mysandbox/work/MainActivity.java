package com.mysandbox.work;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 主界面只负责启动 Android 系统提供的工作资料创建流程。
 * 资料的创建、隔离和后续管理均由系统 Managed Provisioning 负责。
 */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_PROVISIONING_FLOW_FINISHED =
            "state_provisioning_flow_finished";

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private TextView statusText;
    private boolean provisioningFlowFinished;

    private final ActivityResultLauncher<Intent> provisioningLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    provisioningFlowFinished = true;
                    statusText.setText(getString(R.string.status_provisioning_finished));
                    Toast.makeText(this, getString(R.string.toast_provisioning_complete),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, getString(R.string.toast_provisioning_not_completed),
                            Toast.LENGTH_LONG).show();
                    updateAvailabilityStatus();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);
        statusText = findViewById(R.id.text_status);
        provisioningFlowFinished = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_PROVISIONING_FLOW_FINISHED, false);

        Button createButton = findViewById(R.id.button_create_profile);
        createButton.setOnClickListener(v -> startProfileProvisioning());

        Button manageButton = findViewById(R.id.button_open_settings);
        manageButton.setOnClickListener(v -> openWorkProfileSettings());

        if (provisioningFlowFinished) {
            statusText.setText(getString(R.string.status_provisioning_finished));
        } else {
            updateAvailabilityStatus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!provisioningFlowFinished) {
            updateAvailabilityStatus();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PROVISIONING_FLOW_FINISHED, provisioningFlowFinished);
    }

    /**
     * 仅检查当前设备状态是否允许发起系统工作资料创建流程。
     * 不能用 isProfileOwnerApp() 判断个人资料侧是否已经存在工作资料：
     * Profile Owner 位于新建的工作资料用户中，而不是当前个人资料用户中。
     */
    private boolean isProvisioningAllowed() {
        if (dpm == null) {
            return false;
        }
        try {
            return dpm.isProvisioningAllowed(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private void startProfileProvisioning() {
        if (!isProvisioningAllowed()) {
            Toast.makeText(this, getString(R.string.toast_provisioning_unavailable),
                    Toast.LENGTH_LONG).show();
            updateAvailabilityStatus();
            return;
        }

        Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent);
        // 不请求跳过加密；由 Android 系统按设备自身安全策略决定 Provisioning 的加密处理。
        try {
            provisioningLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.toast_provisioning_unavailable),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** 跳转到系统设置，供用户查看或移除工作资料。 */
    private void openWorkProfileSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.toast_settings_unavailable),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAvailabilityStatus() {
        if (isProvisioningAllowed()) {
            statusText.setText(getString(R.string.status_ready_to_provision));
        } else {
            statusText.setText(getString(R.string.status_provisioning_unavailable));
        }
    }
}
