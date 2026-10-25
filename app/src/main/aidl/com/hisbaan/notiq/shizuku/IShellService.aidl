package com.hisbaan.notiq.shizuku;

import android.os.Bundle;

interface IShellService {
    // Transaction code Shizuku calls when it unbinds the user service.
    void destroy() = 16777114;

    // Runs a command as the shell user. Returns "exit" (int) and "output" (stdout + stderr).
    Bundle run(in String[] command) = 1;
}
