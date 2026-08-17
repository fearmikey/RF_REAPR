#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sstream>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>

#include "iperf3/src/iperf.h"
#include "iperf3/src/iperf_api.h"

#define TAG "IperfJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jstring JNICALL
Java_com_fearmikey_rf_1reapr_iperf_IperfNative_runIperfCommand(JNIEnv *env, jobject thiz, jobjectArray args) {
    int argCount = env->GetArrayLength(args);
    std::vector<std::string> cppArgs;
    cppArgs.reserve(argCount + 1);

    // argv[0] is typically the program name
    cppArgs.emplace_back("iperf3");

    std::stringstream commandStr;
    commandStr << "iperf3 ";

    for (int i = 0; i < argCount; ++i) {
        auto string = (jstring) env->GetObjectArrayElement(args, i);
        const char *rawString = env->GetStringUTFChars(string, nullptr);
        cppArgs.emplace_back(rawString);
        commandStr << rawString << " ";
        env->ReleaseStringUTFChars(string, rawString);
        env->DeleteLocalRef(string);
    }

    std::vector<char*> argv;
    argv.reserve(cppArgs.size());
    for (const auto& arg : cppArgs) {
        argv.emplace_back(const_cast<char*>(arg.c_str()));
    }

    LOGI("Executing: %s", commandStr.str().c_str());

    struct iperf_test *test = iperf_new_test();
    if (test == nullptr) {
        LOGE("Failed to create iperf test");
        return env->NewStringUTF("Error: failed to create iperf test");
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

    if (mem_out != nullptr) {
        fflush(mem_out);
        fclose(mem_out);
    }

    iperf_free_test(test);

    std::string result(out_buf);
    free(out_buf);

    return env->NewStringUTF(result.c_str());
}