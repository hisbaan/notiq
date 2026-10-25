package com.hisbaan.notiq.shizuku;

import android.os.Bundle;

interface IShellService {
    // Transaction code Shizuku calls when it unbinds the user service.
    void destroy() = 16777114;

    // Runs a command as the shell user. Returns "exit" (int) and "output" (stdout + stderr).
    Bundle run(in String[] command) = 1;

    // The string extra `key` of the current clip's description, or null (no clip, no such extra,
    // or the device is locked). Reads only the description: reading the clip itself would make
    // Android show a "pasted from your clipboard" toast.
    String clipDescriptionExtra(int userId, String key) = 2;
}
