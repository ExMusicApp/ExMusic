#!/usr/bin/env python3
"""Собирает плагины из src/<id>/ в готовые файлы <id>.exm (ZIP с manifest.json в корне).

Перед упаковкой проверяет манифест по тем же правилам, что и приложение (Plugins.kt); права в манифесте не нужны, чтобы ошибка нашлась на ПК,
а не при установке на телефоне. Запуск:  python3 build.py            (все плагины)
                                          python3 build.py exm.sleep-timer
"""
import json, re, sys, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "src"
API_VERSION = 2


def fail(d, msg):
    raise SystemExit(f"{d.name}: {msg}")


def validate_icon(d: Path, m: dict) -> None:
    icon = m.get("icon")
    if not icon:
        return
    p = d / icon
    if not p.is_file():
        fail(d, f"иконка {icon} не найдена")
    if p.suffix.lower() not in (".png", ".jpg", ".jpeg", ".webp"):
        fail(d, "иконка должна быть png, jpg или webp")
    if p.stat().st_size > 512 * 1024:
        fail(d, "иконка больше 512 КБ")
    try:
        from PIL import Image
        with Image.open(p) as im:
            if max(im.size) > 1024:
                fail(d, f"иконка {im.size[0]}x{im.size[1]}: максимум 1024x1024")
    except ImportError:
        pass  # Pillow нет — размер в пикселях проверит приложение при установке


def validate_settings(d: Path, m: dict, contributes: dict, has_lua: bool) -> None:
    st = contributes.get("settings")
    if st is None:
        return
    if not isinstance(st, list):
        fail(d, "settings: нужен массив")
    if st and not has_lua:
        fail(d, "settings: настройки нужны только плагину с Lua")
    if len(st) > 30:
        fail(d, "settings: не больше 30 полей")
    seen = set()
    for i, o in enumerate(st, 1):
        n = f"настройка №{i}"
        key = str(o.get("key", "")).strip()
        if not re.fullmatch(r"[a-z][a-z0-9_]{0,31}", key):
            fail(d, f"{n}: неверный key")
        if key.startswith("hook_"):
            fail(d, f"{n}: ключи с префиксом «hook_» зарезервированы за хуками")
        if key in seen:
            fail(d, f"{n}: ключ {key} повторяется")
        seen.add(key)
        t = o.get("type")
        if t not in ("switch", "slider", "number", "text", "choice", "color", "button", "section", "info"):
            fail(d, f"{n}: неизвестный тип {t}")
        if not (1 <= len(str(o.get("title", "")).strip()) <= 60):
            fail(d, f"{n}: title от 1 до 60 символов")
        if t in ("slider", "number"):
            mn, mx, step = o.get("min", 0), o.get("max", 100), o.get("step", 1)
            if mn >= mx or step <= 0 or abs(mn) > 1e9 or abs(mx) > 1e9 or (mx - mn) / step > 100000:
                fail(d, f"{n}: неверные min/max/step")
        if t == "color":
            dc = str(o.get("default", "")).strip()
            if dc and not re.fullmatch(r"#[0-9A-Fa-f]{6}", dc):
                fail(d, f"{n}: default цвета — #RRGGBB")
        si = o.get("show_if")
        if si is not None:
            if not isinstance(si, dict) or not re.fullmatch(r"[a-z][a-z0-9_]{0,31}", str(si.get("key", ""))) or si.get("key") == key:
                fail(d, f"{n}: show_if.key — ключ другого поля")
            if not isinstance(si.get("equals"), (bool, int, float, str)):
                fail(d, f"{n}: show_if.equals — true/false, число или строка")
        if t == "choice":
            opts = o.get("options", [])
            if not (2 <= len(opts) <= 12):
                fail(d, f"{n}: options — от 2 до 12 вариантов")
            vals = [x.get("value", "") for x in opts]
            if len(set(vals)) != len(vals) or not all(re.fullmatch(r"[A-Za-z0-9_.-]{1,32}", v) for v in vals):
                fail(d, f"{n}: неверные или повторяющиеся value")

    by_key = {str(o.get("key", "")).strip(): o for o in st}
    for o in st:
        si = o.get("show_if")
        if si is not None:
            tgt = by_key.get(si.get("key"))
            if tgt is None or tgt.get("type") in ("button", "section", "info"):
                fail(d, f"поле {o.get('key')}: show_if ссылается на {si.get('key')}, а такого поля со значением нет")
    entry = contributes.get("settings_entry")
    if entry is not None:
        if not st:
            fail(d, "settings_entry: у плагина нет настроек, открывать нечего")
        if not isinstance(entry, (dict, bool)):
            fail(d, "settings_entry: объект {title, summary} или true")


# Хуки плагин объявляет сам (contributes.hooks: id, title, summary), а их код пишет в Lua: hooks.on(id, function(active) ... end).
# В приложении логики хуков нет. unsafe_java: true — плагину нужна отключённая песочница (Java-код через luajava): пользователь
# включает её вручную в настройках плагина после предупреждения.
MAX_HOOKS = 8


def validate_hooks(d: Path, contributes: dict) -> list:
    hooks = contributes.get("hooks")
    unsafe = contributes.get("unsafe_java", False)
    if not isinstance(unsafe, bool):
        fail(d, "unsafe_java: true или false")
    if hooks is None:
        if unsafe:
            fail(d, "unsafe_java: нужен хотя бы один хук (contributes.hooks)")
        return []
    if not isinstance(hooks, list):
        fail(d, "hooks: нужен массив хуков")
    if len(hooks) > MAX_HOOKS:
        fail(d, f"hooks: не больше {MAX_HOOKS} хуков")
    if unsafe and not hooks:
        fail(d, "unsafe_java: нужен хотя бы один хук (contributes.hooks)")
    seen = []
    for i, h in enumerate(hooks, 1):
        if isinstance(h, str):
            hid = h.strip()
            title = ""
        elif isinstance(h, dict):
            hid = str(h.get("id", "")).strip()
            title = str(h.get("title", "")).strip()
        else:
            fail(d, f"hooks: хук №{i} — строка с id или объект {{id, title, summary}}")
        if not re.fullmatch(r"[a-z][a-z0-9_]{0,31}", hid) or hid in ("enabled", "unsafe"):
            fail(d, f"hooks: id хука №{i} — латиница в нижнем регистре, цифры и _ (до 32 символов; «enabled» и «unsafe» заняты)")
        if hid in seen:
            fail(d, f"hooks: хук «{hid}» повторяется")
        if len(title) > 60:
            fail(d, f"hooks: title хука «{hid}» — до 60 символов")
        seen.append(hid)
    return seen


def check_hook_code(d: Path, entry: str, declared: list) -> None:
    """Хуки объявлены, а кода нет (или наоборот) — почти наверняка ошибка: предупреждаем, но сборку не рвём."""
    src = ""
    for f in d.rglob("*.lua"):
        src += f.read_text(encoding="utf-8", errors="ignore") + "\n"
    for hid in declared:
        if not re.search(r"hooks\.on\s*\(\s*[\"']" + re.escape(hid) + r"[\"']", src):
            print(f"предупреждение: {d.name}: хук «{hid}» объявлен, но в Lua нет hooks.on(\"{hid}\", ...): он не будет действовать")


def validate(d: Path) -> dict:
    mp = d / "manifest.json"
    if not mp.is_file():
        raise SystemExit(f"{d.name}: нет manifest.json")
    m = json.loads(mp.read_text(encoding="utf-8"))
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{2,63}", m.get("id", "")):
        raise SystemExit(f"{d.name}: неверный id")
    if not (1 <= len(m.get("name", "").strip()) <= 60):
        raise SystemExit(f"{d.name}: название от 1 до 60 символов")
    if not (1 <= len(m.get("version", "").strip()) <= 24):
        raise SystemExit(f"{d.name}: версия от 1 до 24 символов")
    api = m.get("api", API_VERSION)
    if not (1 <= api <= API_VERSION):
        raise SystemExit(f"{d.name}: api должен быть от 1 до {API_VERSION}")
    contributes = m.get("contributes", {})
    lua = contributes.get("lua")
    if lua is None:
        raise SystemExit(f"{d.name}: плагин должен содержать Lua-скрипт: contributes.lua = {{\"main\": \"main.lua\"}}")
    entry = lua if isinstance(lua, str) else lua.get("main", "")
    if not entry.endswith(".lua"):
        raise SystemExit(f"{d.name}: contributes.lua.main должен указывать на .lua файл")
    if not (d / entry).is_file():
        raise SystemExit(f"{d.name}: файл {entry} не найден")
    if (d / entry).stat().st_size > 512 * 1024:
        raise SystemExit(f"{d.name}: скрипт больше 512 КБ")
    declared_hooks = validate_hooks(d, contributes)
    check_hook_code(d, entry, declared_hooks)
    validate_settings(d, m, contributes, True)
    validate_icon(d, m)
    for f in d.rglob("*"):
        if f.is_file() and (".." in f.relative_to(d).parts or "\\" in str(f.relative_to(d))):
            raise SystemExit(f"{d.name}: недопустимое имя файла {f}")
    return m


def build(d: Path) -> Path:
    m = validate(d)
    out = HERE / f"{m['id']}.exm"
    files = sorted(p for p in d.rglob("*") if p.is_file())
    files.sort(key=lambda p: (p.name != "manifest.json", str(p)))  # manifest.json первым
    if len(files) > 256:
        raise SystemExit(f"{d.name}: больше 256 файлов")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for p in files:
            z.write(p, p.relative_to(d).as_posix())
    print(f"ok  {out.name}  ({out.stat().st_size} байт, файлов: {len(files)})")
    return out


if __name__ == "__main__":
    names = sys.argv[1:] or sorted(p.name for p in SRC.iterdir() if p.is_dir())
    for n in names:
        build(SRC / n)
