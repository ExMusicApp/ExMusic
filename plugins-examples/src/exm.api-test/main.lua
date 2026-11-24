-- Тест API: проверяет, что основные функции доступны и работают.
-- Кнопки в карточке плагина → событие "action" (key = ключ настройки-кнопки).
-- ui.toast ограничен (не чаще раза в 1,5 с), поэтому итог собирается в одно сообщение.

local ACCENTS = { "#E53935", "#43A047", "#1E88E5", "#FB8C00", "#8E24AA" }
local accent_i = 0
local last_id

local function log_line(...)
  log("[api-test]", ...)
end

-- Один тест: возвращает true/false и текст ошибки.
local function check(results, name, fn)
  local ok, err = pcall(fn)
  if ok and err ~= false then
    results[#results + 1] = { name = name, ok = true }
  else
    results[#results + 1] = { name = name, ok = false, err = tostring(err) }
  end
end

local function run_tests()
  local r = {}

  check(r, "plugin", function()
    assert(plugin.id == "exm.api-test", "plugin.id")
    assert(plugin.name ~= "", "plugin.name")
    assert(plugin.version ~= "", "plugin.version")
  end)

  check(r, "app", function()
    assert(type(app.version) == "string" and app.version ~= "", "app.version")
    assert(type(app.api) == "number" and app.api >= 2, "app.api")
  end)

  check(r, "plugin.read_file", function()
    local s = plugin.read_file("manifest.json")
    assert(type(s) == "string" and s:find("exm.api-test", 1, true), "manifest.json не прочитан")
    assert(plugin.read_file("нет-такого-файла.txt") == nil, "несуществующий файл должен дать nil")
  end)

  check(r, "json", function()
    local s = json.encode({ a = 1, b = { 1, 2, 3 }, c = "x" })
    local t = json.decode(s)
    assert(t.a == 1 and t.c == "x" and #t.b == 3, "круг encode/decode")
    local bad = json.decode("{ это не json")
    assert(bad == nil, "плохой json должен дать nil")
  end)

  check(r, "storage", function()
    storage.set("t_num", 42)
    storage.set("t_str", "строка")
    storage.set("t_tbl", { x = 1, list = { "a", "b" } })
    assert(storage.get("t_num") == 42, "число")
    assert(storage.get("t_str") == "строка", "строка")
    local t = storage.get("t_tbl")
    assert(t.x == 1 and t.list[2] == "b", "таблица")
    assert(storage.get("t_missing", "def") == "def", "значение по умолчанию")
    local found = false
    for _, k in ipairs(storage.keys()) do if k == "t_num" then found = true end end
    assert(found, "keys")
    storage.delete("t_num"); storage.delete("t_str"); storage.delete("t_tbl")
    assert(storage.get("t_num") == nil, "delete")
  end)

  check(r, "settings", function()
    assert(type(settings.get("announce")) == "boolean", "switch")
    assert(type(settings.get("level")) == "number", "slider")
    assert(type(settings.get("count")) == "number", "number")
    assert(type(settings.get("note")) == "string", "text")
    local m = settings.get("mode")
    assert(m == "a" or m == "b" or m == "c", "choice")
  end)

  check(r, "os.date / timer", function()
    assert(os.date("%Y") + 0 >= 2026, "os.date")
    local t = os.date("*t")
    assert(t.year and t.month and t.day, "os.date('*t')")
    assert(timer.now() > 1700000000000, "timer.now")
  end)

  check(r, "string / math", function()
    assert(("abc"):upper() == "ABC", "string")
    assert(math.floor(3.7) == 3 and math.max(1, 5) == 5, "math")
    assert(table.concat({ "a", "b" }, "-") == "a-b", "table.concat")
  end)

  -- Песочница: опасных возможностей в окружении плагина быть не должно.
  check(r, "песочница", function()
    assert(io == nil and debug == nil and load == nil and loadstring == nil and dofile == nil, "io/debug/load/dofile")
    assert(package == nil and collectgarbage == nil, "package/collectgarbage")
    assert(os.execute == nil and os.exit == nil and os.remove == nil, "опасные функции os")
    assert(getmetatable("") == nil, "getmetatable строки")
    assert(not pcall(string.rep, "x", 2000000), "string.rep без ограничения")
    assert(not pcall(string.gsub, ("x"):rep(10000), ".", ("y"):rep(100000)), "string.gsub без ограничения")
    assert(not pcall(table.unpack, {}, 1, 100000), "table.unpack без ограничения")
  end)

  -- Сеть: локальные адреса, http и «хитрые» формы адресов должны отклоняться ещё до запроса.
  check(r, "сеть: локальные адреса", function()
    local bad = {
      "https://127.0.0.1/", "https://192.168.0.1/", "https://10.0.0.1/", "https://[::1]/",
      "https://localhost/", "https://router.local/", "http://example.com/",
    }
    for _, u in ipairs(bad) do
      assert(not pcall(http.get, u), "разрешён адрес " .. u)
    end
  end)

  check(r, "player", function()
    assert(type(player.is_playing()) == "boolean", "is_playing")
    assert(type(player.position()) == "number", "position")
    assert(type(player.duration()) == "number", "duration")
    local v = player.volume()
    assert(v >= 0 and v <= 1, "volume 0..1")
    assert(player.speed() > 0, "speed")
  end)

  local passed, lines = 0, {}
  for _, x in ipairs(r) do
    if x.ok then
      passed = passed + 1
    else
      lines[#lines + 1] = x.name .. ": " .. x.err
      log_line("FAIL", x.name, x.err)
    end
  end
  log_line("самотест:", passed .. "/" .. #r)
  storage.set("last_run", { time = os.date("%Y-%m-%d %H:%M:%S"), passed = passed, total = #r })

  if passed == #r then
    ui.toast("Самотест: все " .. #r .. " проверок пройдены")
  else
    ui.toast("Самотест: " .. passed .. "/" .. #r .. ". Ошибка — " .. lines[1])
  end
end

local function show_state()
  local t = player.current()
  if not t then
    ui.toast("Плеер: трек не выбран")
    return
  end
  local pos = math.floor(player.position() / 1000)
  local dur = math.floor(player.duration() / 1000)
  ui.toast(string.format("%s — %s · %d:%02d / %d:%02d · громкость %d%% · ×%.2f%s",
    t.artists, t.title, math.floor(pos / 60), pos % 60, math.floor(dur / 60), dur % 60,
    math.floor(player.volume() * 100 + 0.5), player.speed(),
    player.is_playing() and "" or " · пауза"))
end

local function next_accent()
  accent_i = accent_i + 1
  if accent_i > #ACCENTS then
    accent_i = 0
    theme.set_accent(nil)
    ui.toast("Акцент: как в приложении")
  else
    theme.set_accent(ACCENTS[accent_i])
    ui.toast("Акцент " .. ACCENTS[accent_i])
  end
end

-- ===== события =====

events.on("track_changed", function(track)
  if track.id == last_id then return end
  last_id = track.id
  local n = (storage.get("tracks_seen") or 0) + 1
  storage.set("tracks_seen", n)
  log_line("track_changed", track.artists, track.title)
  if settings.get("announce") then
    ui.toast("Тест API · трек №" .. n .. ": " .. track.title)
  end
end)

events.on("play_state", function(is_playing)
  log_line("play_state", tostring(is_playing))
  if settings.get("announce") then
    ui.toast(is_playing and "Тест API: играет" or "Тест API: пауза")
  end
end)

events.on("ended", function()
  log_line("ended")
end)

events.on("setting_changed", function(key, value)
  log_line("setting_changed", key, tostring(value))
  ui.toast("Настройка «" .. key .. "» = " .. tostring(value))
end)

events.on("action", function(key)
  if key == "run_tests" then
    run_tests()
  elseif key == "show_state" then
    show_state()
  elseif key == "accent" then
    next_accent()
  elseif key == "reset" then
    storage.clear()
    theme.set_accent(nil)
    accent_i = 0
    last_id = nil
    ui.toast("Данные теста сброшены")
  end
end)

events.on("unload", function()
  theme.set_accent(nil)
  log_line("unload")
end)

log_line("загружен, версия приложения", app.version)
