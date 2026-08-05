// 仅桌面端入口；移动端由 lib.rs 的 mobile_entry_point 处理
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    knowflow_desktop::run()
}
