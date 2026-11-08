cmake_minimum_required(VERSION 3.22.1)
project(abpc_hookprobe LANGUAGES C)

add_library(abpc_hookprobe SHARED hook_probe.c)
target_compile_options(abpc_hookprobe PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_options(abpc_hookprobe PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(abpc_hookprobe PRIVATE dl)
