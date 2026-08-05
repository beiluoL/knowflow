use std::net::TcpStream;
use std::sync::{Arc, Mutex};
use std::thread::sleep;
use std::time::Duration;

use tauri::Emitter;
use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};
use tauri::menu::{MenuBuilder, MenuItemBuilder, SubmenuBuilder, PredefinedMenuItem};
use tauri_plugin_shell::ShellExt;
use tauri_plugin_notification::NotificationExt;
use tauri_plugin_updater::UpdaterExt;
use serde_json::json;

const BACKEND_PORT: u16 = 8787;

/// 共享状态：复习提醒开关（菜单可切换，后台调度线程读取）
struct AppState {
    reminder_enabled: Arc<Mutex<bool>>,
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            // 1) 打开主窗口（生产起 Node 侧车，开发加载 vite）
            #[cfg(not(debug_assertions))]
            {
                let resolver = app.path();
                let resource_dir = resolver.resource_dir()?;
                let web_dir = resource_dir.join("web");
                let api_index = resource_dir.join("api").join("index.js");
                let data_dir = resolver.app_data_dir()?;
                std::fs::create_dir_all(&data_dir)?;

                let sidecar = app
                    .shell()
                    .sidecar("server")
                    .expect("未找到 Node 侧车，请先运行 scripts/prepare-bin.sh")
                    .args([
                        api_index.to_str().unwrap(),
                        "--port",
                        &BACKEND_PORT.to_string(),
                        "--web-dir",
                        web_dir.to_str().unwrap(),
                        "--data-dir",
                        data_dir.to_str().unwrap(),
                    ]);
                let _child = sidecar.spawn().expect("启动后端侧车失败");
                // 阻塞等待后端就绪后再建窗口，确保窗口加载时后端已在监听，避免出现空白/错误页
                wait_for_backend(BACKEND_PORT);

                WebviewWindowBuilder::new(
                    app,
                    "main",
                    WebviewUrl::External(format!("http://127.0.0.1:{BACKEND_PORT}").parse().unwrap()),
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
            app.manage(AppState { reminder_enabled: reminder_enabled.clone() });

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
            start_reminder_scheduler(app.handle().clone(), reminder_enabled.clone());

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![check_for_update])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

/// 轮询后端 TCP 端口，最多等待约 15 秒，确保窗口加载时后端已在监听
fn wait_for_backend(port: u16) {
    for _ in 0..75 {
        if TcpStream::connect(("127.0.0.1", port)).is_ok() {
            sleep(Duration::from_millis(300));
            return;
        }
        sleep(Duration::from_millis(200));
    }
}

/// 后台线程：周期性检查待复习卡片，必要时弹原生通知
fn start_reminder_scheduler(app: tauri::AppHandle, enabled: Arc<Mutex<bool>>) {
    std::thread::spawn(move || loop {
        sleep(Duration::from_secs(30 * 60));
        if !*enabled.lock().unwrap() {
            continue;
        }
        if let Some((count, sample)) = fetch_due_count() {
            if count > 0 {
                show_review_notification(&app, count, &sample);
            }
        }
    });
}

/// 调用后端 /api/workbench/reviews/due-count
fn fetch_due_count() -> Option<(u32, Vec<String>)> {
    let url = format!("http://127.0.0.1:{}/api/workbench/reviews/due-count", BACKEND_PORT);
    let resp = ureq::get(&url)
        .timeout(Duration::from_secs(3))
        .call()
        .ok()?;
    let v: serde_json::Value = resp.into_json().ok()?;
    let count = v.get("count")?.as_u64()? as u32;
    let sample: Vec<String> = v
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
