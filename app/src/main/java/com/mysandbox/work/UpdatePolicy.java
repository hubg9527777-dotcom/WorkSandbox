package com.mysandbox.work;

import java.io.IOException;

/** Version and download rules independent of Android UI/network state. */
final class UpdatePolicy {
    private UpdatePolicy() {}

    static String downloadUrl(String tag) throws IOException {
        if (tag == null || !tag.matches("v[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")) {
            throw new IOException("Invalid release tag");
        }
        return "https://github.com/hubg9527777-dotcom/WorkSandbox/releases/download/"
                + tag + "/WorkSandbox-" + tag + ".apk";
    }

    static int compareVersions(String left, String right) throws IOException {
        if (left == null || right == null
                || !left.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
                || !right.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")) {
            throw new IOException("Invalid version");
        }
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (result != 0) return result;
        }
        return 0;
    }

}
