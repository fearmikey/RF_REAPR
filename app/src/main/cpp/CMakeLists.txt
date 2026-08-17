cmake_minimum_required(VERSION 3.22.1)

project("iperf_jni")

# Import the prebuilt libcrypto and libssl if we wanted full features.
# To keep this minimal and simple for this implementation, we will compile iperf3 without OpenSSL.
# This disables authentication and TLS features of iperf3, which is usually fine for local/dongle tests.

# Configure iperf build options
add_definitions(-DHAVE_CONFIG_H)

# Define the source files for iperf3.
# In a real environment, you'd clone the full iperf source tree. For this PoC,
# we'll assume we have the core files or use an existing library.
# Since getting the full iperf3 source to compile via CMake natively without configure scripts
# is highly complex, we're going to create a dummy implementation to demonstrate the architecture
# or pull from an existing NDK library if needed.

# We will implement the wrapper directly here for now to simulate the JNI binding.
add_library(
        iperf_jni
        SHARED
        iperf_jni.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        iperf_jni
        ${log-lib}
)
