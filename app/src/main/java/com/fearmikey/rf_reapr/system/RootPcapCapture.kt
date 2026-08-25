package com.fearmikey.rf_reapr.system

import android.content.Context
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RootPcapCapture(private val context: Context) {
    private var tcpdumpProcess: Process? = null

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
    
    fun isTcpdumpAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "which tcpdump"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun startCapture(): String? {
        if (!isRootAvailable() || !isTcpdumpAvailable()) {
            Log.e("RootPcap", "Root or tcpdump not available")
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "root_capture_$timestamp.pcap"
        val pcapDir = File(context.filesDir, "pcaps")
        if (!pcapDir.exists()) pcapDir.mkdirs()
        
        val outputFile = File(pcapDir, fileName)
        val outputPath = outputFile.absolutePath

        try {
            // -i any = listen on all interfaces
            // -w = write to file
            // -U = "packet-buffered" (write to file immediately rather than batching)
            val command = "tcpdump -i any -w $outputPath -U\n"
            
            tcpdumpProcess = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(tcpdumpProcess?.outputStream)
            
            os.writeBytes(command)
            os.flush()
            // We do NOT write "exit\n" because we want the process to stay alive and capture
            
            return outputPath
        } catch (e: Exception) {
            Log.e("RootPcap", "Error starting tcpdump", e)
            return null
        }
    }

    fun stopCapture() {
        try {
            if (tcpdumpProcess != null) {
                // To cleanly stop tcpdump running under su, we kill it explicitly
                val killProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "killall tcpdump"))
                killProcess.waitFor()
                
                try {
                    val os = DataOutputStream(tcpdumpProcess?.outputStream)
                    os.writeBytes("exit\n")
                    os.flush()
                    os.close()
                } catch(_: Exception) {}
                
                tcpdumpProcess?.destroy()
                tcpdumpProcess = null
            }
        } catch (e: Exception) {
            Log.e("RootPcap", "Error stopping tcpdump", e)
        }
    }
}
