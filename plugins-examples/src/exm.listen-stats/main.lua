-- Статистика прослушиваний.
-- Данные лежат в storage плагина (при удалении плагина стираются):
--   day      — { date = "2026-11-15", count = 7 }
--   artists  — { ["Имя исполнителя"] = число прослушиваний, ... }

local MAX_ARTISTS = 100   -- сколько исполнителей помнить

local last_id

local function today()
  return os.date("%Y-%m-%d")
end

local function bump_day()
  local day = storage.get("day")
  if type(day) ~= "table" or day.date ~= today() then
    day = { date = today(), count = 0 }
  end
  day.count = day.count + 1
  storage.set("day", day)
  return day.count
end

local function bump_artists(names)
  if names == "" then return end
  local artists = storage.get("artists")
  if type(artists) ~= "table" then artists = {} end
  local n = 0
  for _ in pairs(artists) do n = n + 1 end
  if artists[names] == nil and n >= MAX_ARTISTS then return end
  artists[names] = (artists[names] or 0) + 1
  storage.set("artists", artists)
end

events.on("track_changed", function(track)
  if track.id == last_id then return end
  last_id = track.id
  local count = bump_day()
  bump_artists(track.artists)
  local every = settings.get("milestone")
  if settings.get("cheer") and every > 0 and count % every == 0 then
    ui.toast("Сегодня уже " .. count .. " треков. Хороший день для музыки!")
  end
end)

-- Кнопка «Сбросить статистику» в карточке плагина.
events.on("action", function(key)
  if key == "reset" then
    storage.clear()
    last_id = nil
    ui.toast("Статистика сброшена")
  end
end)
