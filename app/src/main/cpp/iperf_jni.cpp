#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sstream>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <getopt.h>
#include <mutex>
#include <sys/socket.h>
#include <csignal>
#include <setjmp.h>

extern "C" {
#include "iperf3/src/iperf.h"
#include "iperf3/src/iperf_api.h"
#include "iperf3/src/timer.h"
}

#define TAG "IperfJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Define the global jmp_buf for iperf3 error handling
jmp_buf env;

static struct iperf_test *active_test = nullptr;
static std::mutex active_test_mutex;

extern "C"
JNIEXPORT jstring JNICALL
Java_com_fearmikey_rf_1reapr_iperf_IperfNative_runIperfCommand(JNIEnv *jniEnv, jobject thiz, jobjectArray args) {
    int argCount = jniEnv->GetArrayLength(args);
    std::vector<std::string> cppArgs;
    cppArgs.reserve(argCount + 1);

    // argv[0] is typically the program name
    cppArgs.emplace_back("iperf3");

    std::stringstream commandStr;
    commandStr << "iperf3 ";

    for (int i = 0; i < argCount; ++i) {
        auto string = (jstring) jniEnv->GetObjectArrayElement(args, i);
        const char *rawString = jniEnv->GetStringUTFChars(string, nullptr);
        cppArgs.emplace_back(rawString);
        commandStr << rawString << " ";
        jniEnv->ReleaseStringUTFChars(string, rawString);
        jniEnv->DeleteLocalRef(string);
    }

    std::vector<char*> argv;
    argv.reserve(cppArgs.size());
    for (const auto& arg : cppArgs) {
        argv.emplace_back(const_cast<char*>(arg.c_str()));
        LOGI("Arg: %s", arg.c_str());
    }

    LOGI("Executing: %s", commandStr.str().c_str());

    // Ignore SIGPIPE to prevent the app from crashing if a socket closes unexpectedly
    signal(SIGPIPE, SIG_IGN);

    struct iperf_test *test = iperf_new_test();
    if (test == nullptr) {
        LOGE("Failed to create iperf test");
        return jniEnv->NewStringUTF("Error: failed to create iperf test");
    }

    {
        std::lock_guard<std::mutex> lock(active_test_mutex);
        active_test = test;
    }

    iperf_defaults(test);

    // Use a large memory buffer for output
    const size_t buf_size = 1024 * 1024; // 1MB
    char* out_buf = (char*)malloc(buf_size);
    memset(out_buf, 0, buf_size);
    FILE* mem_out = fmemopen(out_buf, buf_size, "w");
    if (mem_out != nullptr) {
        test->outfile = mem_out;
    }

    // Reset global state for subsequent calls in the same process
    optind = 1;
    i_errno = 0;
    errarg = nullptr;

    // Destroy any leftover timers from previous aborted runs
    tmr_destroy();

    if (setjmp(env) == 0) {
        int parse_res = iperf_parse_arguments(test, static_cast<int>(argv.size()), argv.data());
        if (parse_res < 0) {
            LOGE("Failed to parse arguments: %s", iperf_strerror(i_errno));
            if (mem_out) {
                fprintf(mem_out, "Error parsing arguments: %s\n", iperf_strerror(i_errno));
            }
        } else {
            if (test->role == 'c') {
                if (iperf_run_client(test) < 0) {
                    LOGE("iperf_run_client failed: %s", iperf_strerror(i_errno));
                    if (mem_out) {
                        fprintf(mem_out, "iperf_run_client failed: %s\n", iperf_strerror(i_errno));
                    }
                }
            } else if (test->role == 's') {
                if (iperf_run_server(test) < 0) {
                    LOGE("iperf_run_server failed: %s", iperf_strerror(i_errno));
                    if (mem_out) {
                        fprintf(mem_out, "iperf_run_server failed: %s\n", iperf_strerror(i_errno));
                    }
                }
            } else {
                 if (mem_out) {
                     fprintf(mem_out, "Error: Unknown role. Use -c or -s.\n");
                 }
            }
        }
    } else {
        LOGE("Caught iperf3 exit attempt via longjmp");
        if (mem_out) {
            fprintf(mem_out, "\niperf3 terminated unexpectedly.\n");
        }
    }

    if (mem_out != nullptr) {
        fflush(mem_out);
    }

    // Prevent iperf_free_test from closing our mem_out stream
    if (test != nullptr) {
        test->outfile = stdout;
    }

    {
        std::lock_guard<std::mutex> lock(active_test_mutex);
        active_test = nullptr;
    }

    iperf_free_test(test);
    test = nullptr;

    // Destroy all timers to prevent global state leaks crashing the next test run
    tmr_destroy();

    // Reset signal handlers to default to prevent stale pointers from crashing later
    signal(SIGPIPE, SIG_DFL);
    signal(SIGINT, SIG_DFL);
    signal(SIGTERM, SIG_DFL);
    signal(SIGHUP, SIG_DFL);
    signal(SIGUSR1, SIG_DFL);

    if (mem_out != nullptr) {
        fclose(mem_out);
    }

    // Ensure the output buffer is null-terminated before creating std::string
    out_buf[buf_size - 1] = '\0';
    std::string result(out_buf);
    free(out_buf);

    return jniEnv->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_fearmikey_rf_1reapr_iperf_IperfNative_stopIperf(JNIEnv *jniEnv, jobject thiz) {
    std::lock_guard<std::mutex> lock(active_test_mutex);
    if (active_test != nullptr) {
        active_test->done = 1;

        // Use shutdown() to break out of blocking system calls without triggering fdsan double-close errors.
        // iperf3 will perform the actual close() during its cleanup phase.
        if (active_test->listener > 0) {
            shutdown(active_test->listener, SHUT_RDWR);
        }
        if (active_test->ctrl_sck > 0) {
            shutdown(active_test->ctrl_sck, SHUT_RDWR);
        }
        if (active_test->prot_listener > 0) {
            shutdown(active_test->prot_listener, SHUT_RDWR);
        }
    }
}