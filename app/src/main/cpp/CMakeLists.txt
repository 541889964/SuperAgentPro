cmake_minimum_required(VERSION 3.18.1)
project("pow")
add_library(pow SHARED pow.cpp)
target_link_libraries(pow log)
