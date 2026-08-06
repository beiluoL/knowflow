use std::net::TcpListener;
use std::sync::{Arc, Mutex};
use std::thread::sleep;
use std::time::Duration;

use tauri::Emitter;
use tauri::{Manager, RunEvent, WebviewUrl, WebviewWindowBuilder};
use tauri::menu::{MenuBuilder, MenuItemBuilder, SubmenuBuilder, PredefinedMenuItem};
use tauri_plugin_shell::ShellExt;
#[cfg(not(debug_assertions))]
use tauri_plugin_shell::process::{CommandChild, CommandEvent};
use tauri_plugin_notification::NotificationExt;
use tauri_plugin_updater::UpdaterExt;
use serde_json::json;

/// 首选端口。仅作「首选」——真实端口以侧车 stdout 上报为准，
/// 避免端口被占用时后端自动让位、而窗口仍加载旧端口造成白屏。
const PREFERRED_PORT: u16 = 8787;

/// 共享状态：复习提醒开关 + 后端真实端口 + 侧车句柄（退出时需杀掉，防止孤儿进程占端口）
struct AppState {
    #[allow(dead_code)]
    reminder_enabled: Arc<Mutex<bool>>,
    backend_port: Arc<Mutex<u16>>,
    #[cfg(not(debug_assertions))]
    backend_child: Arc<Mutex<Option<CommandChild>>>,
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            let backend_port = Arc::new(Mutex::new(PREFERRED_PORT));
            #[cfg(not(debug_assertions))]
            let backend_child: Arc<Mutex<Option<CommandChild>>> = Arc::new(Mutex::new(None));

            // 1) 打开主窗口（生产起 Node 侧车，开发加载 vite）
            #[cfg(not(debug_assertions))]
            {
                let resolver = app.path();
                let resource_dir = resolver.resource_dir()?;
                let web_dir = resource_dir.join("web");
                let api_index = resource_dir.join("api").join("index.js");
                let data_dir = resolver.app_data_dir()?;
                std::fs::create_dir_all(&data_dir)?;

                // 首选 8787（保持 origin 稳定）；若已被占用则直接换一个空闲端口，
                // 不再依赖后端自增让位，从源头杜绝「窗口端口 ≠ 后端端口」。
                let want_port = if port_available(PREFERRED_PORT) {
                    PREFERRED_PORT
                } else {
                    pick_free_port()
                };

                let sidecar = app
                    .shell()
                    .sidecar("server")
                    .expect("未找到 Node 侧车，请先运行 scripts/prepare-bin.sh")
                    .args([
                        api_index.to_str().unwrap(),
                        "--port",
                        &want_port.to_string(),
                        "--web-dir",
                        web_dir.to_str().unwrap(),
                        "--data-dir",
                        data_dir.to_str().unwrap(),
                    ]);
                let (mut rx, child) = sidecar.spawn().expect("启动后端侧车失败");
                *backend_child.lock().unwrap() = Some(child);

                // 侧车 stdout 会打印 "API on http://127.0.0.1:<port>"，以它上报的端口为准
                let (tx, port_rx) = std::sync::mpsc::channel::<u16>();
                tauri::async_runtime::spawn(async move {
                    let mut reported = false;
                    while let Some(event) = rx.recv().await {
                        match event {
                            CommandEvent::Stdout(line) => {
                                let s = String::from_utf8_lossy(&line).to_string();
                                if !reported {
                                    if let Some(p) = parse_reported_port(&s) {
                                        let _ = tx.send(p);
                                        reported = true;
                                    }
                                }
                                println!("[api] {}", s.trim_end());
                            }
                            CommandEvent::Stderr(line) => {
                                eprintln!("[api:err] {}", String::from_utf8_lossy(&line).trim_end());
                            }
                            CommandEvent::Terminated(p) => {
                                eprintln!("[api] 侧车退出: code={:?}", p.code);
                            }
                            _ => {}
                        }
                    }
                });

                let real_port = port_rx
                    .recv_timeout(Duration::from_secs(20))
                    .unwrap_or(want_port);
                *backend_port.lock().unwrap() = real_port;

                // HTTP 级健康检查（不再只探 TCP，避免被无关监听器骗过导致白屏）
                if !wait_for_backend(real_port) {
                    eprintln!("[api] 后端在 {real_port} 上未通过健康检查，窗口可能空白");
                }

                WebviewWindowBuilder::new(
                    app,
                    "main",
                    WebviewUrl::External(format!("http://127.0.0.1:{real_port}").parse().unwrap()),
                )
                .title("KnowFlow 学习工作台")
                .inner_size(1200.0, 800.0)
                .build()?;
            }

            #[cfg(debug_assertions)]
            {
                WebviewWindowBuilder::new(
                    app,
                    "main",
                    WebviewUrl::External("http://localhost:5173".parse().unwrap()),
                )
                .title("KnowFlow 学习工作台 (dev)")
                .inner_size(1200.0, 800.0)
                .build()?;
            }

            // 2) 共享状态
            let reminder_enabled = Arc::new(Mutex::new(true));
            app.manage(AppState {
                reminder_enabled: reminder_enabled.clone(),
                backend_port: backend_port.clone(),
                #[cfg(not(debug_assertions))]
                backend_child: backend_child.clone(),
            });

            // 3) 原生菜单（macOS 首个子菜单即 App 菜单）
            let about = PredefinedMenuItem::about(app, Some("关于 KnowFlow 学习工作台"), None)?;
            let quit = MenuItemBuilder::with_id("quit", "退出").build(app)?;
            let check_update = MenuItemBuilder::with_id("check_update", "检查更新…").build(app)?;
            let go_review = MenuItemBuilder::with_id("go_review", "去学习复习").build(app)?;
            let toggle_reminder = MenuItemBuilder::with_id("toggle_reminder", "复习提醒：开").build(app)?;
            let reload = MenuItemBuilder::with_id("reload", "重新加载页面").build(app)?;

            let app_menu = SubmenuBuilder::new(app, "KnowFlow")
                .item(&about)
                .separator()
                .item(&check_update)
                .item(&go_review)
                .item(&toggle_reminder)
                .separator()
                .item(&quit)
                .build()?;

            let view_menu = SubmenuBuilder::new(app, "视图")
                .item(&reload)
                .build()?;

            let menu = MenuBuilder::new(app)
                .items(&[&app_menu, &view_menu])
                .build()?;
            app.set_menu(menu)?;

            // 4) 菜单事件处理
            let reminder_enabled_ev = reminder_enabled.clone();
            let toggle_item = toggle_reminder.clone();
            app.on_menu_event(move |app, event| match event.id().as_ref() {
                "quit" => app.exit(0),
                "check_update" => {
                    let h = app.clone();
                    tauri::async_runtime::spawn(async move {
                        let _ = do_check_update(h).await;
                    });
                }
                "go_review" => {
                    if let Some(w) = app.get_webview_window("main") {
                        let _ = w.show();
                        let _ = w.set_focus();
                    }
                    let _ = app.emit("navigate", "/reviews");
                }
                "toggle_reminder" => {
                    let mut en = reminder_enabled_ev.lock().unwrap();
                    *en = !*en;
                    let label = if *en { "复习提醒：开" } else { "复习提醒：关" };
                    let _ = toggle_item.set_text(label);
                }
                "reload" => {
                    if let Some(w) = app.get_webview_window("main") {
                        let _ = w.eval("location.reload()");
                    }
                }
                _ => {}
            });

            // 5) 后台复习提醒调度（每 30 分钟轮询后端，有待复习卡片则弹原生通知）
            start_reminder_scheduler(app.handle().clone(), reminder_enabled.clone(), backend_port.clone());

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![check_for_update])
        .build(tauri::generate_context!())
        .expect("error while building tauri application");

    // 退出时杀掉 Node 侧车：否则它会变成孤儿进程长期占住端口，
    // 导致下次启动窗口连到旧后端而白屏（本次白屏的根因）。
    app.run(|app_handle, event| {
        if let RunEvent::Exit = event {
            #[cfg(not(debug_assertions))]
            if let Some(state) = app_handle.try_state::<AppState>() {
                if let Some(child) = state.backend_child.lock().unwrap().take() {
                    let _ = child.kill();
                }
            }
            let _ = app_handle;
        }
    });
}

/// 端口是否可用（能独占绑定即视为空闲）
fn port_available(port: u16) -> bool {
    TcpListener::bind(("127.0.0.1", port)).is_ok()
}

/// 让操作系统分配一个空闲端口
fn pick_free_port() -> u16 {
    TcpListener::bind("127.0.0.1:0")
        .ok()
        .and_then(|l| l.local_addr().ok())
        .map(|a| a.port())
        .unwrap_or(PREFERRED_PORT)
}

/// 从侧车 stdout 行中解析 "http://127.0.0.1:<port>"
fn parse_reported_port(line: &str) -> Option<u16> {
    let marker = "127.0.0.1:";
    let idx = line.find(marker)? + marker.len();
    let digits: String = line[idx..].chars().take_while(|c| c.is_ascii_digit()).collect();
    if digits.is_empty() {
        return None;
    }
    digits.parse::<u16>().ok()
}

/// HTTP 级健康检查：必须真的拿到 200 才认为后端就绪。
/// 旧实现只做 TCP connect，会被任何占用该端口的无关进程骗过，从而加载出白屏。
fn wait_for_backend(port: u16) -> bool {
    let url = format!("http://127.0.0.1:{port}/api/workbench/overview");
    for _ in 0..100 {
        if let Ok(resp) = ureq::get(&url).timeout(Duration::from_secs(2)).call() {
            if resp.status() == 200 {
                sleep(Duration::from_millis(150));
                return true;
            }
        }
        sleep(Duration::from_millis(200));
    }
    false
}

/// 后台线程：周期性检查待复习卡片，必要时弹原生通知
fn start_reminder_scheduler(
    app: tauri::AppHandle,
    enabled: Arc<Mutex<bool>>,
    port: Arc<Mutex<u16>>,
) {
    std::thread::spawn(move || loop {
        sleep(Duration::from_secs(30 * 60));
        if !*enabled.lock().unwrap() {
            continue;
        }
        let p = *port.lock().unwrap();
        if let Some((count, sample)) = fetch_due_count(p) {
            if count > 0 {
                show_review_notification(&app, count, &sample);
            }
        }
    });
}

/// 调用后端 /api/workbench/reviews/due-count
fn fetch_due_count(port: u16) -> Option<(u32, Vec<String>)> {
    let url = format!("http://127.0.0.1:{port}/api/workbench/reviews/due-count");
    let resp = ureq::get(&url)
        .timeout(Duration::from_secs(3))
        .call()
        .ok()?;
    let v: serde_json::Value = resp.into_json().ok()?;
    // 后端统一信封 {code, data}，计数在 data 内
    let data = v.get("data")?;
    let count = data.get("count")?.as_u64()? as u32;
    let sample: Vec<String> = data
        .get("sample")?
        .as_array()?
        .iter()
        .filter_map(|x| x.as_str().map(|s| s.to_string()))
        .collect();
    Some((count, sample))
}

/// 弹出复习提醒通知。
/// 注：tauri-plugin-notification 2.3.x 桌面端 builder 不提供 on_click 回调，
/// 点击通知会由系统原生聚焦应用到前台；跳转到复习页可走菜单「去学习复习」或侧边栏导航。
fn show_review_notification(app: &tauri::AppHandle, count: u32, sample: &[String]) {
    let app = app.clone();
    let mut body = format!("你有 {} 张卡片待复习", count);
    if !sample.is_empty() {
        body.push_str("：");
        body.push_str(&sample.join("、"));
    }
    let _ = app
        .notification()
        .builder()
        .title("KnowFlow 复习提醒")
        .body(body)
        .show();
}

/// 检查并安装更新（菜单「检查更新」与前端命令共用）
async fn do_check_update(app: tauri::AppHandle) -> Result<serde_json::Value, String> {
    let updater = app.updater().map_err(|e| e.to_string())?;
    match updater.check().await {
        Ok(Some(update)) => {
            let newv = update.version.to_string();
            update
                .download_and_install(
                    |_downloaded: usize, _total: Option<u64>| {},
                    || {},
                )
                .await
                .map_err(|e| e.to_string())?;
            let _ = app
                .notification()
                .builder()
                .title("更新已安装")
                .body(format!("已升级到版本 {}", newv))
                .show();
            Ok(json!({ "update": true, "to": newv }))
        }
        Ok(None) => {
            let _ = app
                .notification()
                .builder()
                .title("已是最新")
                .body("当前已是最新版本")
                .show();
            Ok(json!({ "update": false }))
        }
        Err(e) => Err(e.to_string()),
    }
}

/// 供前端调用的「检查更新」命令（侧边栏按钮使用）
#[tauri::command]
async fn check_for_update(app: tauri::AppHandle) -> Result<serde_json::Value, String> {
    do_check_update(app).await
}
