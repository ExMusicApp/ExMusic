-- Стенд для плагинов: заглушки API (по образу LuaPlugins.kt) + сценарии.
local ROOT = ...  -- папка plugins-examples/src

local function read(path)
  local f = assert(io.open(path, "rb")); local s = f:read("a"); f:close(); return s
end

-- Мини-декодер JSON только для стенда (в приложении json.decode делает org.json).
local function json_decode(s)
  local i = 1
  local function ws() i = s:find("%S", i) or #s + 1 end
  local value
  local function str()
    local j = s:find('"', i + 1, true); local r = s:sub(i + 1, j - 1); i = j + 1; return r
  end
  function value()
    ws()
    local c = s:sub(i, i)
    if c == "{" then
      local t = {}; i = i + 1; ws()
      if s:sub(i, i) == "}" then i = i + 1; return t end
      while true do
        ws(); local k = str(); ws(); assert(s:sub(i, i) == ":"); i = i + 1
        t[k] = value(); ws()
        local d = s:sub(i, i); i = i + 1
        if d == "}" then return t end
      end
    elseif c == "[" then
      local t = {}; i = i + 1; ws()
      if s:sub(i, i) == "]" then i = i + 1; return t end
      while true do
        t[#t + 1] = value(); ws()
        local d = s:sub(i, i); i = i + 1
        if d == "]" then return t end
      end
    elseif c == '"' then return str()
    elseif s:sub(i, i + 3) == "true" then i = i + 4; return true
    elseif s:sub(i, i + 4) == "false" then i = i + 5; return false
    else
      local num = s:match("^-?%d+%.?%d*", i); i = i + #num; return tonumber(num)
    end
  end
  return value()
end

local function deepcopy(v)
  if type(v) ~= "table" then return v end
  local r = {}; for k, x in pairs(v) do r[k] = deepcopy(x) end; return r
end

local ALL_PERMS = { player = 1, ui = 1, storage = 1, net = 1, search = 1, theme = 1, wave = 1 }

-- Создаёт «приложение»: состояние + функция load(dir) → запускает плагин.
local function new_host(dir, hour_ref)
  local manifest = json_decode(read(dir .. "/manifest.json"))
  local H = {
    toasts = {}, accent = nil, amoled = nil, stations = {}, timers = {}, handlers = {},
    store = {}, dialogs = {}, playing = false, volume = 1.0, paused_calls = 0, logs = {},
  }
  local perms = {}
  for p in pairs(ALL_PERMS) do perms[p] = true end  -- права в манифесте не нужны: плагину доступно всё API

  local env = {}
  for _, n in ipairs { "assert", "error", "ipairs", "pairs", "next", "pcall", "xpcall", "select", "setmetatable",
                       "tonumber", "tostring", "type", "unpack", "rawget", "rawset", "rawequal" } do env[n] = _G[n] end
  env.string = deepcopy(string); env.table = deepcopy(table); env.math = deepcopy(math)
  env._G = env
  local function logf(...) local t = {}; for i = 1, select("#", ...) do t[#t + 1] = tostring((select(i, ...))) end; H.logs[#H.logs + 1] = table.concat(t, "\t") end
  env.print = logf; env.log = logf
  env.os = {
    time = os.time, clock = os.clock,
    date = function(fmt, t)
      fmt = fmt or "%c"
      if fmt == "%H" then return string.format("%02d", hour_ref.h) end
      if fmt == "%Y-%m-%d" then return hour_ref.day or "2026-11-15" end
      return os.date(fmt, t)
    end,
  }
  env.json = { decode = function(s) return json_decode(s) end, encode = function() error("не нужен стенду") end }
  env.plugin = {
    id = manifest.id, name = manifest.name, version = manifest.version, author = manifest.author,
    read_file = function(name)
      local f = io.open(dir .. "/" .. name, "rb"); if not f then return nil end
      local s = f:read("a"); f:close(); return s
    end,
  }
  env.app = { version = "test", api = 2 }
  -- settings: как в рантайме — по описанию из манифеста, неизвестный ключ и кнопка дают ошибку
  local sdef, svals = {}, {}
  for _, st in ipairs(((manifest.contributes or {}).settings) or {}) do
    sdef[st.key] = st; if st.type ~= "button" and st.type ~= "section" and st.type ~= "info" then svals[st.key] = st.default end
  end
  env.settings = { get = function(k)
    local st = sdef[k]
    if not st or st.type == "button" or st.type == "section" or st.type == "info" then error("settings.get: в манифесте нет настройки «" .. tostring(k) .. "»") end
    return svals[k]
  end,
  all = function() local t = {}; for k, v in pairs(svals) do t[k] = v end; return t end,
  set = function(k, v)
    local st = sdef[k]
    if not st or st.type == "button" or st.type == "section" or st.type == "info" then error("settings.set: в манифесте нет настройки «" .. tostring(k) .. "»") end
    svals[k] = v; return true
  end }
  -- Хуки: как в приложении. Плагин объявляет хуки в contributes.hooks, приложение добавляет настройки (главный выключатель
  -- hook_enabled по умолчанию ВЫКЛЮЧЕН, при unsafe_java — hook_unsafe, по выключателю на хук), а код хука — это
  -- hooks.on(id, function(active)) из самого плагина: приложение вызывает его с true/false при включении/выключении.
  local declared_hooks = {}
  local contrib = manifest.contributes or {}
  for _, h in ipairs(contrib.hooks or {}) do
    local hid = type(h) == "table" and h.id or h
    declared_hooks[hid] = true
    sdef["hook_" .. hid] = { key = "hook_" .. hid, type = "switch", default = true }; svals["hook_" .. hid] = true
  end
  if next(declared_hooks) then sdef.hook_enabled = { key = "hook_enabled", type = "switch", default = false }; svals.hook_enabled = false end
  if contrib.unsafe_java then sdef.hook_unsafe = { key = "hook_unsafe", type = "switch", default = false }; svals.hook_unsafe = false end
  H.hook_fns, H.hook_on = {}, {}
  local function hook_allowed(id) return declared_hooks[id] and svals.hook_enabled == true and svals["hook_" .. id] ~= false end
  env.hooks = {
    on = function(id, f)
      assert(type(id) == "string" and type(f) == "function", "hooks.on")
      if not declared_hooks[id] then error("hooks.on: хук «" .. id .. "» не объявлен в manifest.json (contributes.hooks)") end
      H.hook_fns[id] = f
    end,
    off = function(id) local had = H.hook_fns[id] ~= nil; H.hook_fns[id] = nil; H.hook_on[id] = nil; return had end,
    refresh = function(id) H.eval_hooks(id, true) end,
    enabled = function(id) return hook_allowed(id) and true or false end,
  }
  -- Как LuaInst.evalHooks: обработчик зовётся только при смене состояния (или по refresh для включённого хука).
  function H.eval_hooks(only, refresh)
    for id in pairs(declared_hooks) do
      local f = H.hook_fns[id]
      if f and (only == nil or only == id) then
        local want, now = hook_allowed(id) and true or false, H.hook_on[id] or false
        if want ~= now or (want and refresh) then H.hook_on[id] = want; f(want) end
      end
    end
  end
  function H.stop_hooks()
    for id, on in pairs(H.hook_on) do if on then H.hook_on[id] = nil; H.hook_fns[id](false) end end
  end
  -- Песочница: app.viewmodel() и luajava есть только при hook_enabled и hook_unsafe (как LuaInst.unsafeOn).
  H.unsafe_on = function() return contrib.unsafe_java and svals.hook_enabled == true and svals.hook_unsafe == true end
  env.timer = {
    after = function(ms, f) assert(ms >= 0 and type(f) == "function"); H.timers[#H.timers + 1] = { ms = math.max(500, ms), f = f, rep = false }; return #H.timers end,
    every = function(ms, f) assert(type(f) == "function"); H.timers[#H.timers + 1] = { ms = math.max(500, ms), f = f, rep = true }; return #H.timers end,
    cancel = function(id) H.timers[id] = nil end,
    now = function() return 0 end,
  }
  env.events = { on = function(name, f)
    local ok = { track_changed = 1, play_state = 1, ended = 1, unload = 1, setting_changed = 1, action = 1 }
    assert(ok[name], "неизвестное событие " .. tostring(name))
    local needp = { track_changed = 1, play_state = 1, ended = 1 }
    if needp[name] and not perms.player then error("для события «" .. name .. "» нужно право «player»") end
    H.handlers[name] = H.handlers[name] or {}; table.insert(H.handlers[name], f)
  end }
  if perms.ui then env.ui = { toast = function(t) H.toasts[#H.toasts + 1] = t end, alert = function(title, text, cb)
      H.alerts = H.alerts or {}; H.alerts[#H.alerts + 1] = { title = title, text = text }
      H.dialogs[#H.dialogs + 1] = { title = title, text = text, buttons = { { title = "Понятно", on_click = cb } }, on_dismiss = cb }
      return #H.dialogs
    end,
    confirm = function(title, text, yes, no)
      assert(type(yes) == "function", "confirm: on_yes")
      H.dialogs[#H.dialogs + 1] = { title = title, text = text, buttons = { { title = "Отмена", on_click = no }, { title = "ОК", on_click = yes } }, on_dismiss = no }
      return #H.dialogs
    end,
    prompt = function(title, text, value, ok, cancel)
      assert(type(ok) == "function", "prompt: on_submit")
      H.dialogs[#H.dialogs + 1] = { title = title, text = text, input = value, buttons = { { title = "Отмена", on_click = cancel }, { title = "ОК", on_click = ok } }, on_dismiss = cancel }
      return #H.dialogs
    end,
    dialog = function(d)
      assert(type(d) == "table" and (not d.buttons or #d.buttons <= 3), "dialog")
      H.dialogs[#H.dialogs + 1] = d; return #H.dialogs
    end,
    sheet = function(d)
      assert(d.title and type(d.render) == "function", "sheet")
      local id = d.id or "sheet"; H.pages = H.pages or {}; H.pages[id] = d; H.open = id; return id
    end,
    open_page = function(id) assert(H.pages and H.pages[id], "open_page"); H.open = id end,
    close_page = function(id)
      if H.open and (id == nil or id == H.open) then
        local d = H.pages[H.open]; H.open = nil
        if d and d.on_close then d.on_close() end
      end
    end,
    settings_page = function(d)
      assert(type(d) == "table" and d.id:match("^[a-z][a-z0-9_]*$") and #d.title >= 1 and type(d.render) == "function", "settings_page")
      H.pages = H.pages or {}; H.pages[d.id] = d
    end,
    refresh = function() end,
    remove_page = function(id) if H.pages then H.pages[id] = nil end end } end
  if perms.storage then env.storage = {
    get = function(k, d) local v = H.store[k]; if v == nil then return d end; return deepcopy(v) end,
    set = function(k, v) H.store[k] = deepcopy(v) end,
    delete = function(k) H.store[k] = nil end,
    clear = function() H.store = {} end,
  } end
  if perms.theme then env.theme = {
    set_accent = function(c) assert(c == nil or c:match("^#%x%x%x%x%x%x$")); H.accent = c end,
    set_amoled = function(b) H.amoled = b end,
  } end
  if perms.wave then env.wave = {
    clear_stations = function() H.stations = {} end,
    add_station = function(title, st, sub)
      assert(#title >= 1 and #title <= 40, "title")
      assert(st:match("^[a-z_]+:[%w_.:%-]+$"), "station " .. st)
      H.stations[#H.stations + 1] = { title = title, station = st, subtitle = sub }
    end,
    start = function() end,
  } end
  if perms.player then env.player = {
    play = function() H.playing = true end, pause = function() H.playing = false; H.paused_calls = H.paused_calls + 1 end,
    toggle = function() H.playing = not H.playing end, next = function() end, previous = function() end,
    seek = function() end, position = function() return 0 end, duration = function() return 0 end,
    is_playing = function() return H.playing end,
    volume = function() return H.volume end, set_volume = function(v) assert(v >= 0 and v <= 1, "volume " .. v); H.volume = v end,
    speed = function() return 1 end, set_speed = function() end, current = function() return nil end,
  } end
  if perms.search then env.app.search = function() end end
  -- app.viewmodel() существует только при отключённой песочнице; сам объект подставляет тест (H.vm).
  env.app.viewmodel = function() if not H.unsafe_on() then error("attempt to call a nil value (field 'viewmodel')") end; return H.vm end

  -- require как в рантайме: <name>.lua внутри плагина, результат кэшируется
  local modules = {}
  env.require = function(name)
    if modules[name] then return modules[name] end
    local src = env.plugin.read_file(name:gsub("%.", "/") .. ".lua")
    assert(src, "модуль не найден: " .. name)
    modules[name] = true
    local chunk = assert(load(src, "@" .. name .. ".lua", "t", env))
    local r = chunk(name); if r == nil then r = true end; modules[name] = r; return r
  end

  function H.start()
    local chunk = assert(load(read(dir .. "/main.lua"), "@main.lua", "t", env))
    chunk()
    H.eval_hooks()   -- как LuaInst.boot: после запуска скрипта включаем разрешённые хуки
  end
  function H.set(k, v)
    assert(sdef[k], "нет настройки " .. k); svals[k] = v
    if k:sub(1, 5) == "hook_" then H.eval_hooks() end
    H.emit("setting_changed", k, v)
  end
  function H.action(k) assert(sdef[k] and sdef[k].type == "button", "нет кнопки " .. k); H.emit("action", k) end
  function H.emit(name, ...) for _, f in ipairs(H.handlers[name] or {}) do f(...) end end
  -- Как приложение: render() страницы → список компонентов; действие пользователя → колбэк и новая отрисовка.
  function H.render(id) local l = H.pages[id].render(); H.last = l; return l end
  function H.node(id, nid) for _, n in ipairs(H.render(id)) do if n.id == nid then return n end end end
  function H.touch(id, nid, v)
    local n = assert(H.node(id, nid), "нет компонента " .. nid)
    local cb = n.on_change or n.on_click; assert(cb, "нет колбэка у " .. nid); cb(v)
  end
  -- Ответ пользователя на диалог: button = номер кнопки (с 1), nil — закрыл без выбора; input — текст поля.
  function H.answer(i, button, input)
    local d = assert(H.dialogs[i], "нет диалога " .. i); H.dialogs[i] = false
    local cb = button and d.buttons[button] and d.buttons[button].on_click or (not button and d.on_dismiss)
    if cb then if input ~= nil then cb(input) else cb() end end
  end
  function H.fire_timers() for _, t in pairs(H.timers) do t.f() end end
  return H
end

local pass, fail = 0, 0
local function check(cond, msg)
  if cond then pass = pass + 1 else fail = fail + 1; print("  ПРОВАЛ: " .. msg) end
end
local function section(n) print("== " .. n) end

-- 1. Тема по времени суток
do
  section("exm.time-theme")
  local hr = { h = 8 }
  local H = new_host(ROOT .. "/exm.time-theme", hr); H.start()
  check(H.accent == "#FFB74D" and H.amoled == false, "утро")
  hr.h = 13; H.fire_timers(); check(H.accent == "#4FC3F7" and H.amoled == false, "день")
  hr.h = 19; H.fire_timers(); check(H.accent == "#BA68C8", "вечер")
  hr.h = 23; H.fire_timers(); check(H.accent == "#5C6BC0" and H.amoled == true, "ночь 23")
  hr.h = 3;  H.fire_timers(); check(H.accent == "#5C6BC0" and H.amoled == true, "ночь 3")
  hr.h = 5;  H.fire_timers(); check(H.accent == "#FFB74D", "5:00 — уже утро")
  hr.h = 22; H.fire_timers(); check(H.amoled == true, "22:00 — уже ночь")
  hr.h = 21; H.fire_timers(); check(H.accent == "#BA68C8" and H.amoled == false, "21:59 — вечер")
  check(#H.timers == 1 and H.timers[1].ms == 60000, "один таймер раз в минуту")
  H.set("amoled_night", false)
  check(H.accent == "#BA68C8" and H.amoled == false, "настройка применяется сразу (вечер)")
  hr.h = 23; H.set("amoled_night", false)
  check(H.accent == "#5C6BC0" and H.amoled == false, "ночью без AMOLED, если выключено")
  H.set("amoled_night", true); check(H.amoled == true, "включили — AMOLED сразу")
end

-- 2. Что играет
do
  section("exm.track-toast")
  local H = new_host(ROOT .. "/exm.track-toast", { h = 12 }); H.start()
  H.emit("track_changed", { id = "1", title = "Песня", artists = "Группа" })
  H.emit("track_changed", { id = "1", title = "Песня", artists = "Группа" })
  H.emit("track_changed", { id = "2", title = "Без артиста", artists = "" })
  check(#H.toasts == 2, "два уведомления (дубль подавлен), а не " .. #H.toasts)
  check(H.toasts[1] == "Играет: Группа — Песня", "формат: " .. tostring(H.toasts[1]))
  check(H.toasts[2] == "Играет: Без артиста", "без артиста: " .. tostring(H.toasts[2]))
  H.set("format", "title"); H.emit("track_changed", { id = "3", title = "Т", artists = "А" })
  check(H.toasts[3] == "Играет: Т", "формат title: " .. tostring(H.toasts[3]))
  H.set("format", "artist"); H.emit("track_changed", { id = "4", title = "Т", artists = "А" })
  check(H.toasts[4] == "Играет: А", "формат artist: " .. tostring(H.toasts[4]))
  H.emit("track_changed", { id = "5", title = "Только название", artists = "" })
  check(H.toasts[5] == "Играет: Только название", "artist без исполнителя — название: " .. tostring(H.toasts[5]))
end

-- 3. Статистика
do
  section("exm.listen-stats")
  local hr = { h = 12, day = "2026-11-15" }
  local H = new_host(ROOT .. "/exm.listen-stats", hr); H.start()
  for i = 1, 10 do H.emit("track_changed", { id = tostring(i), title = "t" .. i, artists = (i % 2 == 0) and "A" or "B" }) end
  check(H.store.day.count == 10, "10 за день")
  check(#H.toasts == 1 and H.toasts[1]:find("10 треков"), "поздравление на 10-м: " .. tostring(H.toasts[1]))
  check(H.store.artists.A == 5 and H.store.artists.B == 5, "счётчики артистов")
  H.emit("track_changed", { id = "10", title = "x", artists = "A" })  -- тот же id — не считать
  check(H.store.day.count == 10, "повтор id не считается")
  hr.day = "2026-11-16"
  H.emit("track_changed", { id = "99", title = "x", artists = "A" })
  check(H.store.day.count == 1, "новый день — счёт с 1")
  H.toasts = {}; H.set("milestone", 5)
  for i = 1, 4 do H.emit("track_changed", { id = "m" .. i, title = "x", artists = "A" }) end  -- сегодня уже 1+4 = 5
  check(#H.toasts == 1 and H.toasts[1]:find("5 треков"), "milestone=5: поздравление на 5-м, а не на 10-м")
  H.toasts = {}; H.set("cheer", false)
  for i = 1, 5 do H.emit("track_changed", { id = "c" .. i, title = "x", artists = "A" }) end
  check(#H.toasts == 0, "cheer=false — без поздравлений")
  H.action("reset")
  check(next(H.store) == nil and H.toasts[1] == "Статистика сброшена", "кнопка сброса очищает хранилище")
  for i = 1, 150 do H.emit("track_changed", { id = "n" .. i, title = "x", artists = "Артист" .. i }) end
  local n = 0; for _ in pairs(H.store.artists) do n = n + 1 end
  check(n <= 100, "артистов не больше 100, а " .. n)
end

-- 4. Умная волна
do
  section("exm.smart-wave")
  local hr = { h = 8 }
  local H = new_host(ROOT .. "/exm.smart-wave", hr); H.start()
  check(#H.stations == 2 and H.stations[1].station == "activity:wake-up", "утренние станции")
  hr.h = 12; H.fire_timers()
  check(#H.stations == 2 and H.stations[1].station == "activity:work-background", "дневные заменили утренние, не добавились")
  hr.h = 23; H.fire_timers()
  check(#H.stations == 2 and H.stations[1].station == "activity:fall-asleep", "ночные")
  local before = H.stations; H.fire_timers(); check(H.stations == before, "тот же период — список не перестраивается")
end

-- 5. Таймер сна
do
  section("exm.sleep-timer")
  local H = new_host(ROOT .. "/exm.sleep-timer", { h = 1 }); H.start()
  check(#H.timers == 1 and H.timers[1].ms == 1000, "тик раз в секунду")
  H.playing = true
  local minv = 1
  for s = 1, 1800 - 21 do H.fire_timers() end
  check(H.volume == 1.0 and H.playing, "до затухания громкость нетронута")
  H.fire_timers()   -- left = 20 → начало затухания
  check(H.volume <= 1.0 and H.volume > 0.9 or H.volume == 1.0, "затухание началось плавно: " .. H.volume)
  for s = 1, 19 do H.fire_timers(); minv = math.min(minv, H.volume) end
  check(minv < 0.1 and H.playing, "громкость почти ноль, но ещё играет: " .. minv)
  H.fire_timers()
  check(H.playing == false and H.paused_calls == 1, "пауза в конце")
  check(H.volume == 1.0, "громкость восстановлена: " .. H.volume)
  check(H.toasts[1]:find("30 мин") and H.toasts[#H.toasts]:find("спокойной ночи"), "уведомления в начале и в конце")
  -- пауза → idle; через 10 минут простоя счётчик сбрасывается
  H.toasts = {}; H.playing = false
  for s = 1, 600 do H.fire_timers() end
  H.playing = true; H.fire_timers()
  check(#H.toasts == 1 and H.toasts[1]:find("30 мин"), "после долгой паузы отсчёт заново")
  -- выгрузка во время затухания возвращает громкость
  local H2 = new_host(ROOT .. "/exm.sleep-timer", { h = 1 }); H2.start(); H2.playing = true
  H2.volume = 0.8
  for s = 1, 1790 do H2.fire_timers() end
  check(H2.volume < 0.8, "идёт затухание: " .. H2.volume)
  H2.emit("unload"); check(H2.volume == 0.8, "unload вернул громкость: " .. H2.volume)
  -- настройка minutes меняется «на лету»: ставим 5 минут и проверяем, что пауза наступит через 300 с
  local H3 = new_host(ROOT .. "/exm.sleep-timer", { h = 1 }); H3.start(); H3.playing = true
  H3.set("minutes", 5); H3.set("fade_seconds", 0)
  for s = 1, 299 do H3.fire_timers() end
  check(H3.playing and H3.volume == 1.0, "без затухания громкость не трогаем")
  H3.fire_timers(); check(not H3.playing, "пауза через 5 минут по настройке")
end

-- 6. Демо: страница настроек из Lua
do
  section("exm.settings-demo")
  local H = new_host(ROOT .. "/exm.settings-demo", { h = 12 }); H.start()
  check(H.pages and H.pages.demo and H.pages.demo.title == "Демо настроек", "страница зарегистрирована")
  local l = H.render("demo")
  local function has(id) for _, n in ipairs(l) do if n.id == id then return true end end end
  check(not has("accent") and not has("note"), "условные поля скрыты")
  H.touch("demo", "use_accent", true)
  check(H.accent == "#6750A4", "включили акцент — тема применилась: " .. tostring(H.accent))
  l = H.render("demo"); check(has("accent"), "поле цвета появилось")
  H.touch("demo", "accent", "#FF0000"); check(H.accent == "#FF0000", "цвет из поля")
  H.touch("demo", "mode", "bright"); l = H.render("demo"); check(has("note"), "в ярком режиме есть подпись")
  H.touch("demo", "volume", 40); check(H.store.state.volume == 40, "значение сохранено в storage")
  for _, n in ipairs(H.render("demo")) do if n.type == "button" and n.title == "+1" then n.on_click(); n.on_click() end end
  check(H.store.state.clicks == 2, "кнопка +1 дважды: " .. tostring(H.store.state.clicks))
  for _, n in ipairs(H.render("demo")) do if n.type == "button" and n.title == "Сбросить всё" then n.on_click() end end
  check(H.accent == nil and H.store.state.clicks == 0 and H.alerts and H.alerts[1].title == "Готово", "сброс и окно")
  H.answer(#H.dialogs, 1); check(H.toasts[#H.toasts] == "Окно закрыто", "колбэк alert вызван")
  H.store.state.clicks = 5; local stc = H.store.state
  for _, n in ipairs(H.render("demo")) do if n.title == "Подтверждение" then n.on_click() end end
  H.answer(#H.dialogs, 1)                        -- «Отмена»
  check(H.store.state.clicks == 5, "confirm: отмена ничего не меняет")
  for _, n in ipairs(H.render("demo")) do if n.title == "Подтверждение" then n.on_click() end end
  H.answer(#H.dialogs, 2)                        -- «ОК»
  check(H.store.state.clicks == 0, "confirm: ОК сбросил счётчик")
  for _, n in ipairs(H.render("demo")) do if n.title == "Спросить имя" then n.on_click() end end
  check(H.dialogs[#H.dialogs].input == "", "prompt: поле ввода есть")
  H.answer(#H.dialogs, 2, "Аня"); check(H.store.state.name == "Аня", "prompt: имя сохранено")
  for _, n in ipairs(H.render("demo")) do if n.title == "Своё окно" then n.on_click() end end
  local dd = H.dialogs[#H.dialogs]
  check(#dd.buttons == 3 and dd.buttons[1].style == "danger" and dd.buttons[3].style == "primary", "dialog: три кнопки")
  H.answer(#H.dialogs, nil); check(H.toasts[#H.toasts] == "Окно закрыто без выбора", "dialog: on_dismiss")
  for _, n in ipairs(H.render("demo")) do if n.title == "Открыть модалку" then n.on_click() end end
  check(H.open == "counter", "sheet открыта")
  local cl = H.pages.counter.render(); cl[2].on_click(); cl[2].on_click()
  check(H.pages.counter.render()[1].title == "Значение: 2", "состояние модалки")
  H.pages.counter.render()[3].on_click()
  check(H.open == nil and H.toasts[#H.toasts] == "Итого в модалке: 2", "close_page + on_close")
end

-- 7. NoRestrictions: код хука — Java через luajava, песочница отключается пользователем
do
  section("exm.no-restrictions")
  -- Макет Java-объектов: AppViewModel._state → StateFlow.getValue() → UiState.getAccount() → Account.hasPlus (рефлексия).
  local function mk_class(fields) return { getDeclaredField = function(_, n) if fields[n] then return fields[n] end; error("NoSuchFieldException " .. n) end, getSuperclass = function() return nil end } end
  local function mk_field(name) return { setAccessible = function() end,
    get = function(_, o) return o._f[name] end, setBoolean = function(_, o, v) o._f[name] = v end } end
  local acc = { _f = { hasPlus = false } }
  acc.getClass = function() return mk_class { hasPlus = mk_field("hasPlus") } end
  acc.getHasPlus = function() return acc._f.hasPlus end
  local ui = { getAccount = function() return acc end }
  local flow = { getValue = function() return ui end }
  local vm = { _f = { _state = flow } }
  vm.getClass = function() return mk_class { _state = mk_field("_state") } end

  local function boot()
    local H = new_host(ROOT .. "/exm.no-restrictions", { h = 12 }); H.vm = vm; return H
  end
  local H = boot(); H.start()
  check(H.hook_fns.plus_local, "плагин сам регистрирует код хука")
  check(#H.timers == 0 and acc._f.hasPlus == false, "по умолчанию хуки выключены: код не вызывался")
  H.set("hook_enabled", true)
  check(#H.timers == 1 and acc._f.hasPlus == false, "хуки включены, но песочница нет: Java-код недоступен, ничего не меняется")
  H.set("hook_unsafe", true)
  H.set("hook_plus_local", false); H.set("hook_plus_local", true)   -- как после перезапуска плагина с отключённой песочницей
  check(acc._f.hasPlus == true, "песочница отключена и хук включён: Java-код поставил hasPlus")
  H.set("hook_plus_local", false)
  check(acc._f.hasPlus == false, "хук выключен: значение возвращено")
  H.set("hook_plus_local", true); H.set("hook_enabled", false)
  check(acc._f.hasPlus == false, "главный выключатель гасит хук")
  H.set("hook_enabled", true); H.stop_hooks()
  check(acc._f.hasPlus == false, "остановка плагина тоже выключает хук")
end

print(string.format("\nИтого: %d проверок пройдено, %d провалено", pass, fail))
if fail > 0 then error("есть провалы") end
io.stdout:flush()
