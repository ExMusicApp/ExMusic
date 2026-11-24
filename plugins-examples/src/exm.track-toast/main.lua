-- Что играет: уведомление при смене трека.
-- Формат задаётся настройкой format (выбор в карточке плагина).
local last_id

local function describe(track)
  local mode = settings.get("format")
  if mode == "title" then return track.title end
  if mode == "artist" and track.artists ~= "" then return track.artists end
  if track.artists ~= "" then return track.artists .. " — " .. track.title end
  return track.title
end

events.on("track_changed", function(track)
  if track.id == last_id then return end
  last_id = track.id
  ui.toast("Играет: " .. describe(track))
end)
