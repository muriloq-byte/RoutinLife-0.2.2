#!/usr/bin/env python3
"""
Gera o ícone do RoutinLife como VectorDrawable (XML, texto) + uma prévia em SVG.

Desenho: fatia de pizza cartunesca, traço preto grosso com preenchimento branco (sem
sombreamento), levantando uma barra de peso, com rosto sorridente, tênis, linha de chão
e estrelinhas de esforço — mesma linguagem da imagem de referência.

Por que vetor: o ícone é 100% escalável, pesa poucos KB, não depende de nenhuma
ferramenta externa para ser gerado e cobre todas as densidades de tela. O script é a
fonte única do desenho: ele reescreve os XMLs usados pelo app e a prévia em SVG.

Arquivos gerados:
  app/src/main/res/drawable/ic_launcher_foreground.xml      (camada de frente adaptativa)
  app/src/main/res/mipmap-anydpi/ic_launcher.xml             (ícone legado, Android 7)
  app/src/main/res/mipmap-anydpi/ic_launcher_round.xml       (ícone legado redondo)
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml         (ícone adaptativo, Android 8+)
  app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml   (adaptativo redondo)
  docs/icon.svg                                              (prévia para o README)

Uso:  python3 tools/generate_vector_icons.py
"""
import math
import os

INK = "111111"
WHITE = "FFFFFF"
CREAM = "FFF7E8"

K = 0.5522847498  # aproximação de Bézier para um quarto de elipse
VIEWPORT = 108.0  # área de desenho do ícone adaptativo (66/108 é a zona segura)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


# --------------------------------------------------------------------- geometria

def n(value):
    return ("%.1f" % value).rstrip("0").rstrip(".")


def ellipse_path(cx, cy, rx, ry):
    kx, ky = rx * K, ry * K
    return (
        "M {a},{b} C {c},{d} {e},{f} {g},{h} C {i},{j} {k},{l} {m},{n} "
        "C {o},{p} {q},{r} {s},{t} C {u},{v} {w},{x} {y},{z} Z".format(
            a=n(cx - rx), b=n(cy), c=n(cx - rx), d=n(cy - ky), e=n(cx - kx), f=n(cy - ry),
            g=n(cx), h=n(cy - ry), i=n(cx + kx), j=n(cy - ry), k=n(cx + rx), l=n(cy - ky),
            m=n(cx + rx), n=n(cy), o=n(cx + rx), p=n(cy + ky), q=n(cx + kx), r=n(cy + ry),
            s=n(cx), t=n(cy + ry), u=n(cx - kx), v=n(cy + ry), w=n(cx - rx), x=n(cy + ky),
            y=n(cx - rx), z=n(cy),
        )
    )


def rrect_path(x0, y0, x1, y1, r):
    k = r * K
    return (
        "M {a},{b} L {c},{d} C {e},{f} {g},{h} {i},{j} L {k},{l} "
        "C {m},{n} {o},{p} {q},{r} L {s},{t} C {u},{v} {w},{x} {y},{z} L {aa},{ab} "
        "C {ac},{ad} {ae},{af} {ag},{ah} Z".format(
            a=n(x0 + r), b=n(y0), c=n(x1 - r), d=n(y0),
            e=n(x1 - r + k), f=n(y0), g=n(x1), h=n(y0 + r - k), i=n(x1), j=n(y0 + r),
            k=n(x1), l=n(y1 - r), m=n(x1), n=n(y1 - r + k), o=n(x1 - r + k), p=n(y1),
            q=n(x1 - r), r=n(y1), s=n(x0 + r), t=n(y1), u=n(x0 + r - k), v=n(y1),
            w=n(x0), x=n(y1 - r + k), y=n(x0), z=n(y1 - r), aa=n(x0), ab=n(y0 + r),
            ac=n(x0), ad=n(y0 + r - k), ae=n(x0 + r - k), af=n(y0), ag=n(x0 + r), ah=n(y0),
        )
    )


def polygon_path(points):
    return "M " + " L ".join("%s,%s" % (n(x), n(y)) for x, y in points) + " Z"


def polyline_path(points):
    return "M " + " L ".join("%s,%s" % (n(x), n(y)) for x, y in points)


def star_points(cx, cy, r):
    points = []
    for index in range(8):
        angle = math.pi * 2 * index / 8 - math.pi / 2
        radius = r if index % 2 == 0 else r * 0.30
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return points


# ------------------------------------------------------------------ construção

class Icon:
    """Coleta os pathData do ícone; a arte é desenhada em coordenadas normalizadas."""

    def __init__(self, art_scale, shape=None, background=CREAM):
        self.vp = VIEWPORT
        self.art_size = VIEWPORT * art_scale
        self.offset = (VIEWPORT - self.art_size) / 2
        self.paths = []  # (pathData, fill, stroke, strokeWidth)

        if shape == "square":
            self.add(
                rrect_path(0.008 * self.vp, 0.008 * self.vp, 0.992 * self.vp, 0.992 * self.vp,
                           0.220 * self.vp),
                background, INK, 0.020 * self.vp,
            )
        elif shape == "circle":
            self.add(
                ellipse_path(0.5 * self.vp, 0.5 * self.vp, 0.487 * self.vp, 0.487 * self.vp),
                background, INK, 0.020 * self.vp,
            )

    # transformação arte (0..1) -> viewport
    def X(self, u):
        return self.offset + u * self.art_size

    def Y(self, v):
        return self.offset + v * self.art_size

    def S(self, size):
        return size * self.art_size

    def add(self, path, fill, stroke=None, width=0.0):
        self.paths.append((path, fill, stroke, width))

    def line(self, points, color, width):
        self.add(polyline_path([(self.X(u), self.Y(v)) for u, v in points]), None, color, width)

    def tube(self, points, width):
        """Membro do corpo: traço preto com núcleo branco (mesmo estilo do contorno)."""
        self.line(points, INK, width + 2 * self.S(0.016))
        self.line(points, WHITE, width)

    def shape(self, path, fill, stroke=None, width=0.0):
        self.add(path, fill, stroke, width)

    def ellipse(self, u, v, ru, rv, fill, stroke=None, width=0.0):
        self.shape(ellipse_path(self.X(u), self.Y(v), self.S(ru), self.S(rv)), fill, stroke, width)

    def rrect(self, u0, v0, u1, v1, radius, fill, stroke=None, width=0.0):
        self.shape(
            rrect_path(self.X(u0), self.Y(v0), self.X(u1), self.Y(v1), self.S(radius)),
            fill, stroke, width,
        )

    def star(self, u, v, radius, fill, stroke, width):
        self.shape(polygon_path(star_points(self.X(u), self.Y(v), self.S(radius))), fill, stroke, width)

    def arc(self, u, v, ru, rv, start_deg, end_deg, width, steps=12):
        points = []
        for index in range(steps + 1):
            angle = math.radians(start_deg + (end_deg - start_deg) * index / steps)
            points.append((self.X(u) + self.S(ru) * math.cos(angle),
                           self.Y(v) + self.S(rv) * math.sin(angle)))
        self.add(polyline_path(points), None, INK, width)


def draw_art(icon):
    ink = icon.S(0.016)  # espessura do contorno

    # 1. chão
    icon.line([(0.20, 0.95), (0.80, 0.95)], INK, icon.S(0.018))

    # 2. pernas (atrás da fatia)
    icon.tube([(0.46, 0.72), (0.38, 0.87)], icon.S(0.050))
    icon.tube([(0.54, 0.72), (0.62, 0.87)], icon.S(0.050))

    # 3. braços (atrás da barra e da fatia)
    icon.tube([(0.34, 0.25), (0.30, 0.35), (0.36, 0.47)], icon.S(0.045))
    icon.tube([(0.66, 0.25), (0.70, 0.35), (0.64, 0.47)], icon.S(0.045))

    # 4. barra
    icon.line([(0.13, 0.20), (0.87, 0.20)], INK, icon.S(0.034))

    # 5. anilhas
    for cx in (0.145, 0.855):
        icon.ellipse(cx, 0.20, 0.100, 0.100, WHITE, INK, ink)
        icon.ellipse(cx, 0.20, 0.050, 0.050, WHITE, INK, ink)
        icon.ellipse(cx, 0.20, 0.018, 0.018, INK)

    # 6. fatia
    icon.shape(
        polygon_path([(icon.X(0.27), icon.Y(0.46)), (icon.X(0.73), icon.Y(0.46)),
                      (icon.X(0.50), icon.Y(0.80))]),
        WHITE, INK, ink,
    )
    icon.rrect(0.27, 0.375, 0.73, 0.485, 0.050, WHITE, INK, ink)

    # 7. rosto
    icon.ellipse(0.460, 0.575, 0.024, 0.030, INK)
    icon.ellipse(0.540, 0.575, 0.024, 0.030, INK)
    icon.arc(0.500, 0.625, 0.060, 0.040, 15, 165, ink * 1.2)

    # 8. pepperoni
    icon.ellipse(0.365, 0.545, 0.030, 0.030, WHITE, INK, ink)
    icon.ellipse(0.635, 0.545, 0.030, 0.030, WHITE, INK, ink)

    # 9. punhos
    icon.ellipse(0.320, 0.225, 0.055, 0.050, WHITE, INK, ink)
    icon.ellipse(0.680, 0.225, 0.055, 0.050, WHITE, INK, ink)

    # 10. tênis
    icon.rrect(0.240, 0.855, 0.460, 0.925, 0.030, WHITE, INK, ink)
    icon.rrect(0.540, 0.855, 0.760, 0.925, 0.030, WHITE, INK, ink)

    # 11. estrelinhas e pontinhos de esforço
    for u, v, r in ((0.105, 0.420, 0.045), (0.895, 0.450, 0.042),
                    (0.115, 0.660, 0.034), (0.885, 0.690, 0.034)):
        icon.star(u, v, r, WHITE, INK, ink)
    icon.ellipse(0.300, 0.080, 0.012, 0.012, INK)
    icon.ellipse(0.700, 0.075, 0.012, 0.012, INK)


def vector_xml(icon, size_dp):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Gerado por tools/generate_vector_icons.py — não edite à mão. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="%ddp"' % size_dp,
        '    android:height="%ddp"' % size_dp,
        '    android:viewportWidth="%s"' % n(icon.vp),
        '    android:viewportHeight="%s">' % n(icon.vp),
    ]
    for path, fill, stroke, width in icon.paths:
        lines.append('    <path')
        lines.append('        android:fillColor="#FF%s"' % (fill or "000000"))
        if not fill:
            lines[-1] = '        android:fillColor="#00000000"'
        if stroke:
            lines.append('        android:strokeColor="#FF%s"' % stroke)
        if width:
            lines.append('        android:strokeWidth="%s"' % n(width))
            lines.append('        android:strokeLineCap="round"')
            lines.append('        android:strokeLineJoin="round"')
        lines.append('        android:pathData="%s" />' % path)
    lines.append('</vector>')
    return "\n".join(lines) + "\n"


def svg_document(icon):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Gerado por tools/generate_vector_icons.py — não edite à mão. -->',
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" '
        'width="512" height="512" role="img" aria-label="RoutinLife">' % (n(icon.vp), n(icon.vp)),
    ]
    for path, fill, stroke, width in icon.paths:
        attributes = ['        d="%s"' % path]
        attributes.append('        fill="#%s"' % (fill or "none"))
        if stroke:
            attributes.append('        stroke="#%s"' % stroke)
        if width:
            attributes.append('        stroke-width="%s"' % n(width))
            attributes.append('        stroke-linecap="round"')
            attributes.append('        stroke-linejoin="round"')
        lines.append("    <path")
        lines.extend(attributes)
        lines.append('         />')
    lines.append('</svg>')
    return "\n".join(lines) + "\n"


def adaptive_xml():
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Gerado por tools/generate_vector_icons.py — não edite à mão. -->\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/icon_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '</adaptive-icon>\n'
    )


def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
    print("gerado:", os.path.relpath(path, ROOT))


def main():
    res = os.path.join(ROOT, "app", "src", "main", "res")

    # Arte dentro da zona segura do ícone adaptativo (66/108).
    foreground = Icon(art_scale=0.600)
    draw_art(foreground)
    write_file(os.path.join(res, "drawable", "ic_launcher_foreground.xml"),
               vector_xml(foreground, 108))

    # Ícones "legados" (Android 7): fundo creme + arte, em vetor.
    legacy = Icon(art_scale=0.740, shape="square")
    draw_art(legacy)
    write_file(os.path.join(res, "mipmap-anydpi", "ic_launcher.xml"), vector_xml(legacy, 48))

    legacy_round = Icon(art_scale=0.640, shape="circle")
    draw_art(legacy_round)
    write_file(os.path.join(res, "mipmap-anydpi", "ic_launcher_round.xml"),
               vector_xml(legacy_round, 48))

    # Ícone adaptativo (Android 8+).
    write_file(os.path.join(res, "mipmap-anydpi-v26", "ic_launcher.xml"), adaptive_xml())
    write_file(os.path.join(res, "mipmap-anydpi-v26", "ic_launcher_round.xml"), adaptive_xml())

    # Prévia para o README.
    write_file(os.path.join(ROOT, "docs", "icon.svg"), svg_document(legacy))


if __name__ == "__main__":
    main()
