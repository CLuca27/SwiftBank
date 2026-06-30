from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT_DIR = Path(__file__).resolve().parent
WIDTH, HEIGHT = 1080, 1350


def font(size, bold=False):
    font_dir = Path("C:/Windows/Fonts")
    candidates = [
        "segoeuib.ttf" if bold else "segoeui.ttf",
        "arialbd.ttf" if bold else "arial.ttf",
    ]
    for name in candidates:
        path = font_dir / name
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


F = {
    "brand": font(26, True),
    "h1": font(56, True),
    "h2": font(32, True),
    "h3": font(25, True),
    "body": font(21),
    "body_b": font(21, True),
    "small": font(16),
    "small_b": font(16, True),
    "micro": font(13),
    "amount": font(42, True),
    "key": font(32, True),
    "timer": font(28, True),
}


def hex_to_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def gradient_box(img, box, radius, top, bottom):
    x1, y1, x2, y2 = map(int, box)
    w, h = x2 - x1, y2 - y1
    top, bottom = hex_to_rgb(top), hex_to_rgb(bottom)
    grad = Image.new("RGB", (w, h), top)
    gdraw = ImageDraw.Draw(grad)
    for y in range(h):
        t = y / max(h - 1, 1)
        color = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
        gdraw.line((0, y, w, y), fill=color)
    mask = Image.new("L", (w, h), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle((0, 0, w, h), radius=radius, fill=255)
    img.paste(grad, (x1, y1), mask)


def shadow(img, box, radius=34, blur=35, offset=(0, 18), opacity=55):
    x1, y1, x2, y2 = map(int, box)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ldraw = ImageDraw.Draw(layer)
    shifted = (x1 + offset[0], y1 + offset[1], x2 + offset[0], y2 + offset[1])
    ldraw.rounded_rectangle(shifted, radius=radius, fill=(0, 36, 18, opacity))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(layer)


def wrap_text(draw, text, font_obj, max_width):
    words = text.split()
    lines, current = [], ""
    for word in words:
        test = word if not current else f"{current} {word}"
        if draw.textbbox((0, 0), test, font=font_obj)[2] <= max_width:
            current = test
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def text_block(draw, xy, text, font_obj, fill, max_width, line_gap=8):
    x, y = xy
    for line in wrap_text(draw, text, font_obj, max_width):
        draw.text((x, y), line, font=font_obj, fill=fill)
        y += font_obj.size + line_gap
    return y


def canvas():
    img = Image.new("RGBA", (WIDTH, HEIGHT), "#f7fbf8")
    draw = ImageDraw.Draw(img)
    draw.ellipse((-160, -110, 420, 470), fill="#18c970")
    draw.ellipse((760, 0, 1260, 460), fill="#08783e")
    draw.ellipse((560, 1060, 1180, 1500), fill="#18c970")
    return img


def draw_brand(draw, x=78, y=70):
    rounded(draw, (x, y, x + 44, y + 44), 13, "#0ca85a")
    rounded(draw, (x + 9, y + 9, x + 22, y + 34), 5, "#ffffff")
    rounded(draw, (x + 23, y + 23, x + 36, y + 35), 5, "#ffffff")
    draw.text((x + 60, y + 5), "SwiftBank", font=F["brand"], fill="#05351f")


def phone_shell(img, x, y, w=430, h=930):
    draw = ImageDraw.Draw(img)
    shadow(img, (x, y, x + w, y + h), radius=62, blur=42, offset=(0, 22), opacity=75)
    gradient_box(img, (x, y, x + w, y + h), 62, "#111915", "#020705")
    rounded(draw, (x + 14, y + 14, x + w - 14, y + h - 14), 50, "#06110d")
    rounded(draw, (x + 132, y + 8, x + w - 132, y + 31), 0, "#020705")
    return (x + 14, y + 14, x + w - 14, y + h - 14)


def status_bar(draw, screen, dark=False):
    x1, y1, x2, _ = screen
    color = "#ffffff" if not dark else "#ffffff"
    draw.text((x1 + 30, y1 + 25), "9:41", font=F["small_b"], fill=color)
    sx = x2 - 86
    for i, h in enumerate([8, 12, 16]):
        rounded(draw, (sx + i * 8, y1 + 34 - h, sx + i * 8 + 5, y1 + 34), 3, color)
    rounded(draw, (x2 - 43, y1 + 22, x2 - 14, y1 + 36), 5, None, color, 2)
    rounded(draw, (x2 - 39, y1 + 25, x2 - 22, y1 + 33), 3, color)


def draw_auth():
    img = canvas()
    draw = ImageDraw.Draw(img)
    draw_brand(draw)
    draw.text((78, 132), "Acces securizat", font=F["h1"], fill="#07140e")
    text_block(
        draw,
        (78, 208),
        "Autentificare prin PIN sau biometrie, într-un ecran simplu și potrivit pentru prezentarea aplicației.",
        F["body"],
        "#60756a",
        390,
        10,
    )
    screen = phone_shell(img, 560, 88)
    gradient_box(img, screen, 50, "#032416", "#0b8a47")
    draw = ImageDraw.Draw(img)
    status_bar(draw, screen)
    sx1, sy1, sx2, sy2 = screen
    draw.text((sx1 + 34, sy1 + 100), "Bun venit!", font=F["h2"], fill="#ffffff")
    draw.text((sx1 + 34, sy1 + 145), "Introdu codul PIN", font=F["body"], fill=(255, 255, 255, 190))
    dot_y = sy1 + 260
    for i in range(6):
        draw.ellipse((sx1 + 101 + i * 34, dot_y, sx1 + 117 + i * 34, dot_y + 16), outline=(255, 255, 255, 190), width=2)
    cx, cy = (sx1 + sx2) // 2, sy1 + 382
    rounded(draw, (cx - 50, cy - 50, cx + 50, cy + 50), 50, (255, 255, 255, 28), (255, 255, 255, 45), 1)
    for r in [15, 26, 38]:
        draw.arc((cx - r, cy - r, cx + r, cy + r), 200, 520, fill="#caffdf", width=4)
    key_y = sy2 - 402
    nums = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "X", "0", "OK"]
    for idx, val in enumerate(nums):
        row, col = divmod(idx, 3)
        kx = sx1 + 51 + col * 106
        ky = key_y + row * 84
        draw.ellipse((kx, ky, kx + 70, ky + 70), fill="#1b7747")
        bbox = draw.textbbox((0, 0), val, font=F["key"])
        draw.text((kx + 35 - (bbox[2] - bbox[0]) / 2, ky + 33 - (bbox[3] - bbox[1]) / 2 - 4), val, font=F["key"], fill="#ffffff")
    draw.text((sx1 + 142, sy2 - 48), "Ai uitat PIN-ul?", font=F["small"], fill="#d9f7e6")
    shadow(img, (78, 1030, 488, 1210), radius=30, blur=30, offset=(0, 16), opacity=35)
    rounded(draw, (78, 1030, 488, 1210), 30, (255, 255, 255, 230), (255, 255, 255, 255), 1)
    draw.text((108, 1062), "PIN + biometrie", font=F["h3"], fill="#041f13")
    text_block(draw, (108, 1104), "Acces rapid, cu accent pe siguranță și claritate vizuală.", F["body"], "#66786d", 340)
    img.save(OUT_DIR / "swiftbank-auth.png")


def icon_circle(draw, box, label, fill="#153323"):
    rounded(draw, box, 16, fill)
    x1, y1, x2, y2 = box
    bbox = draw.textbbox((0, 0), label, font=F["body_b"])
    draw.text((x1 + (x2 - x1 - bbox[2]) / 2, y1 + (y2 - y1 - bbox[3]) / 2 - 2), label, font=F["body_b"], fill="#40e38c")


def draw_dashboard():
    img = canvas()
    draw = ImageDraw.Draw(img)
    screen = phone_shell(img, 82, 88)
    rounded(draw, screen, 50, "#07100d")
    status_bar(draw, screen)
    draw_brand(draw, 570, 70)
    sx1, sy1, sx2, sy2 = screen
    draw.text((sx1 + 24, sy1 + 86), "Salut, Luca", font=F["h2"], fill="#ffffff")
    rounded(draw, (sx2 - 72, sy1 + 83, sx2 - 30, sy1 + 125), 21, "#0dbb62")
    draw.text((sx2 - 58, sy1 + 90), "L", font=F["body_b"], fill="#ffffff")
    gradient_box(img, (sx1 + 24, sy1 + 144, sx2 - 24, sy1 + 322), 28, "#10c56b", "#05703a")
    draw = ImageDraw.Draw(img)
    draw.text((sx1 + 46, sy1 + 166), "Cont principal · RON", font=F["small"], fill=(255, 255, 255, 205))
    draw.text((sx1 + 46, sy1 + 202), "17.289,55 lei", font=F["amount"], fill="#ffffff")
    draw.text((sx1 + 46, sy1 + 274), "IBAN RO89 SWFT 3072 0859", font=F["small"], fill=(255, 255, 255, 205))
    labels = ["Transfer", "Facturi", "Carduri", "Schimb"]
    glyphs = ["T", "F", "C", "€"]
    for i in range(4):
        x = sx1 + 24 + i * 91
        y = sy1 + 348
        rounded(draw, (x, y, x + 78, y + 76), 22, "#17231d")
        bbox_icon = draw.textbbox((0, 0), glyphs[i], font=F["body_b"])
        draw.text((x + 39 - bbox_icon[2] / 2, y + 10), glyphs[i], font=F["body_b"], fill="#25d97b")
        bbox = draw.textbbox((0, 0), labels[i], font=F["micro"])
        draw.text((x + 39 - (bbox[2] - bbox[0]) / 2, y + 50), labels[i], font=F["micro"], fill="#d9f7e6")
    draw.text((sx1 + 24, sy1 + 466), "Tranzacții recente", font=F["body_b"], fill="#ffffff")
    draw.text((sx2 - 82, sy1 + 470), "Vezi tot", font=F["micro"], fill="#68de9d")
    txs = [
        ("K", "Kaufland", "Alimente · astăzi", "-59,90 lei"),
        ("T", "Transfer primit", "Popescu Andrei", "+250,00 lei"),
        ("F", "Factură internet", "Furnizor salvat", "-80,00 lei"),
    ]
    y = sy1 + 520
    for glyph, title, sub, amount in txs:
        icon_circle(draw, (sx1 + 24, y, sx1 + 70, y + 46), glyph)
        draw.text((sx1 + 84, y - 2), title, font=F["small_b"], fill="#ffffff")
        draw.text((sx1 + 84, y + 24), sub, font=F["micro"], fill=(255, 255, 255, 138))
        bbox = draw.textbbox((0, 0), amount, font=F["small_b"])
        draw.text((sx2 - 24 - (bbox[2] - bbox[0]), y + 10), amount, font=F["small_b"], fill="#ffffff")
        draw.line((sx1 + 24, y + 62, sx2 - 24, y + 62), fill=(255, 255, 255, 28), width=1)
        y += 82
    rounded(draw, (sx1 + 24, sy2 - 88, sx2 - 24, sy2 - 18), 28, "#17231d")
    navs = ["Acasă", "Carduri", "Statistici", "Profil"]
    for i, nav in enumerate(navs):
        fill = "#20d779" if i == 0 else (255, 255, 255, 150)
        bbox = draw.textbbox((0, 0), nav, font=F["micro"])
        nx = sx1 + 58 + i * 87
        draw.text((nx - bbox[2] / 2, sy2 - 60), nav, font=F["micro"], fill=fill)
    draw.text((570, 188), "Dashboard", font=F["h1"], fill="#07140e")
    draw.text((570, 250), "financiar", font=F["h1"], fill="#07140e")
    text_block(draw, (570, 340), "Conturile, soldurile, acțiunile rapide și tranzacțiile recente sunt grupate într-un ecran ușor de urmărit.", F["body"], "#60756a", 420, 10)
    shadow(img, (570, 1035, 980, 1218), radius=30, blur=30, offset=(0, 16), opacity=35)
    rounded(draw, (570, 1035, 980, 1218), 30, (255, 255, 255, 230), "#ffffff", 1)
    draw.text((600, 1068), "Operațiuni rapide", font=F["h3"], fill="#041f13")
    text_block(draw, (600, 1110), "Transferuri, facturi, carduri și schimb valutar accesibile direct din zona principală.", F["body"], "#66786d", 350)
    img.save(OUT_DIR / "swiftbank-dashboard.png")


def draw_payment():
    img = canvas()
    draw = ImageDraw.Draw(img)
    draw_brand(draw)
    draw.text((78, 132), "Confirmare plată", font=F["h1"], fill="#07140e")
    text_block(draw, (78, 208), "Interfață pentru plata online, unde utilizatorul aprobă sau refuză tranzacția din aplicație.", F["body"], "#60756a", 390, 10)
    screen = phone_shell(img, 590, 96)
    rounded(draw, screen, 50, "#050807")
    status_bar(draw, screen)
    sx1, sy1, sx2, sy2 = screen
    rounded(draw, (sx1 + 24, sy1 + 82, sx1 + 66, sy1 + 124), 21, "#142019")
    draw.text((sx1 + 38, sy1 + 88), "‹", font=F["h3"], fill="#ffffff")
    draw.text((sx1 + 78, sy1 + 91), "Confirmare plată", font=F["body_b"], fill="#ffffff")
    rounded(draw, (sx1 + 24, sy1 + 160, sx2 - 24, sy1 + 328), 26, "#1d211f")
    cx, cy = (sx1 + sx2) // 2, sy1 + 232
    draw.ellipse((cx - 53, cy - 53, cx + 53, cy + 53), outline="#11b965", width=7)
    bbox = draw.textbbox((0, 0), "04:58", font=F["timer"])
    draw.text((cx - bbox[2] / 2, cy - 18), "04:58", font=F["timer"], fill="#ffffff")
    draw.text((sx1 + 112, sy1 + 286), "Timp rămas pentru confirmare", font=F["micro"], fill=(255, 255, 255, 138))
    rounded(draw, (sx1 + 24, sy1 + 350, sx2 - 24, sy1 + 578), 26, "#1d211f")
    rounded(draw, (sx1 + 42, sy1 + 370, sx1 + 96, sy1 + 424), 18, "#0b7d40")
    draw.text((sx1 + 55, sy1 + 385), "SB", font=F["small_b"], fill="#ffffff")
    draw.text((sx1 + 112, sy1 + 372), "Magazin Online", font=F["body_b"], fill="#ffffff")
    draw.text((sx1 + 112, sy1 + 404), "Plată cu cardul virtual", font=F["micro"], fill=(255, 255, 255, 142))
    draw.line((sx1 + 42, sy1 + 446, sx2 - 42, sy1 + 446), fill=(255, 255, 255, 24), width=1)
    details = [("Sumă", "1.000,00 lei"), ("Card", "**** 5431"), ("Monedă", "RON")]
    y = sy1 + 466
    for label, value in details:
        draw.text((sx1 + 42, y), label, font=F["small"], fill=(255, 255, 255, 140))
        bbox = draw.textbbox((0, 0), value, font=F["small_b"])
        draw.text((sx2 - 42 - bbox[2], y), value, font=F["small_b"], fill="#ffffff")
        y += 35
    draw.text((sx1 + 120, sy1 + 602), "Aprobă sau refuză plata", font=F["micro"], fill=(255, 255, 255, 155))
    rounded(draw, (sx1 + 24, sy2 - 86, sx1 + 190, sy2 - 28), 29, None, "#0e9a53", 2)
    draw.text((sx1 + 82, sy2 - 67), "Refuză", font=F["small_b"], fill="#ff5d68")
    rounded(draw, (sx2 - 190, sy2 - 86, sx2 - 24, sy2 - 28), 29, "#0b7d40")
    draw.text((sx2 - 131, sy2 - 67), "Aprobă", font=F["small_b"], fill="#ffffff")
    shadow(img, (78, 1025, 380, 1128), radius=24, blur=28, offset=(0, 16), opacity=35)
    rounded(draw, (78, 1025, 380, 1128), 24, "#0b7d40")
    text_block(draw, (105, 1049), "Flux 3D Secure simplificat pentru simulatorul de plăți.", F["body_b"], "#ffffff", 250, 5)
    img.save(OUT_DIR / "swiftbank-payment-confirmation.png")


if __name__ == "__main__":
    draw_auth()
    draw_dashboard()
    draw_payment()
