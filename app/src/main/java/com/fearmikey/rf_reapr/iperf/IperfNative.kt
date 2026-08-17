package com.fearmikey.rf_reapr.iperf

object IperfNative {
    init {
        try {
            System.loadLibrary("iperf_jni")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    /**
     * Executes an iperf3 command using the native JNI bridge.
     * @param args The arguments to pass to iperf3, e.g. arrayOf("-c", "192.168.1.1", "-B", "10.0.0.5")
     * @return The standard output of the iperf command.
     */
    external fun runIperfCommand(args: Array<String>): String
}