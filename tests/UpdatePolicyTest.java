package com.mysandbox.work;

import java.io.IOException;

/** Run with scripts/test-update-policy.sh; no Android device or extra test dependency. */
public final class UpdatePolicyTest {
    public static void main(String[] args) throws Exception {
        require(UpdatePolicy.compareVersions("1.0.10", "1.0.9") > 0, "numeric patch ordering");
        require(UpdatePolicy.compareVersions("1.10.0", "1.9.99") > 0, "numeric minor ordering");
        require(UpdatePolicy.compareVersions("2.0.0", "1.999999.999999") > 0, "major ordering");
        require(UpdatePolicy.compareVersions("1.0.4", "1.0.4") == 0, "same version");
        require(UpdatePolicy.compareVersions("1.0.3", "1.0.4") < 0, "do not downgrade");
        for (String invalid : new String[]{"", "1.0", "1.0.4-beta", "9999999.0.0", "-1.0.0", null}) {
            try {
                UpdatePolicy.compareVersions(invalid, "1.0.4");
                throw new AssertionError("accepted invalid version");
            } catch (IOException expected) { }
        }
        require(UpdatePolicy.downloadUrl("v1.0.5").equals(
                "https://github.com/hubg9527777-dotcom/WorkSandbox/releases/download/v1.0.5/WorkSandbox-v1.0.5.apk"), "official asset only");
        for (String tag : new String[]{"../../evil", "v1.0.5?next=evil", "https://evil.example/a.apk", "v1.0.5/evil", "v1.0.5-beta", null}) {
            try {
                UpdatePolicy.downloadUrl(tag);
                throw new AssertionError("accepted unsafe tag");
            } catch (IOException expected) { }
        }
        System.out.println("Update policy tests passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
