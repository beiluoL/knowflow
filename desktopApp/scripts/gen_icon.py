import zlib, struct

W = H = 512
BG = (0x3B, 0x6F, 0xE0)   # 品牌蓝 #3B6FE0
CARD = (0xFF, 0xFF, 0xFF) # 白色卡片
IX0, IX1 = 120, 392        # 卡片内缩范围

def px(x, y):
    # 圆角近似：四角 40px 范围内淡出为透明，做出圆角方形图标
    corner = 44
    in_x = IX0 <= x < IX1
    in_y = IX0 <= y < IX1
    if in_x and in_y:
        return CARD + (255,)
    # 背景：四角圆角（超出圆角半径则透明）
    def dist_to_corner(cx, cy):
        dx = max(cx - x, x - cx, 0)
        dy = max(cy - y, y - cy, 0)
        return (dx * dx + dy * dy) ** 0.5
    corners = [(corner, corner), (W - corner, corner), (corner, H - corner), (W - corner, H - corner)]
    r = corner
    outside = False
    for (cx, cy) in corners:
        if (x < cx and y < cy) or (x > cx and y < cy) or (x < cx and y > cy) or (x > cx and y > cy):
            # 仅当落在某个圆角象限内才评估
            pass
    # 简化：背景填满，仅当处于任意圆角象限且到圆心距离 > r 时透明
    alpha = 255
    for (cx, cy) in corners:
        in_corner_quad = (x < cx and y < cy) or (x > cx and y < cy) or (x < cx and y > cy) or (x > cx and y > cy)
        if in_corner_quad:
            dx = x - cx
            dy = y - cy
            if (dx * dx + dy * dy) > r * r:
                alpha = 0
    return BG + (alpha,)

raw = bytearray()
for y in range(H):
    raw.append(0)
    for x in range(W):
        r, g, b, a = px(x, y)
        raw += bytes((r, g, b, a))

def chunk(typ, data):
    return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff)

png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
png += chunk(b"IEND", b"")
out = "/Users/beiluo/Documents/alProject/qoderProject/knowflow/desktopApp/icon-source.png"
with open(out, "wb") as f:
    f.write(png)
print("wrote", out, len(png), "bytes")
