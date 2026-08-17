cmake_minimum_required(VERSION 3.22.1)

project("iperf_jni")

# Configure iperf build options
add_definitions(-DHAVE_CONFIG_H)
add_definitions(-D_GNU_SOURCE)
add_definitions(-DWITHOUT_OPENSSL)

# Include directories
include_directories(iperf3/src)

# iperf3 source files
set(IPERF3_SOURCES
    iperf3/src/cjson.c
    iperf3/src/iperf_api.c
    iperf3/src/iperf_error.c
    iperf3/src/iperf_auth.c
    iperf3/src/iperf_client_api.c
    iperf3/src/iperf_locale.c
    iperf3/src/iperf_server_api.c
    iperf3/src/iperf_tcp.c
    iperf3/src/iperf_udp.c
    iperf3/src/iperf_sctp.c
    iperf3/src/iperf_util.c
    iperf3/src/iperf_time.c
    iperf3/src/iperf_pthread.c
    iperf3/src/dscp.c
    iperf3/src/net.c
    iperf3/src/tcp_info.c
    iperf3/src/timer.c
    iperf3/src/units.c
)

# Build a shared library combining iperf3 and JNI wrapper
add_library(
        iperf_jni
        SHARED
        iperf_jni.cpp
        ${IPERF3_SOURCES}
)

find_library(
        log-lib
        log
)

target_link_libraries(
        iperf_jni
        ${log-lib}
)
