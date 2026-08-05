# 应用图标

`tauri build` 需要图标文件。请准备一张 1024×1024 的 PNG，然后运行：

```bash
npm run tauri icon /path/to/icon.png
```

该命令会自动生成 Tauri 所需的全部图标（icon.png、icon.ico、icon.icns 及各尺寸），
并放置在本目录下，无需手动创建。
