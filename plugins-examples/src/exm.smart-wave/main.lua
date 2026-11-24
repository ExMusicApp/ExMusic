-- Умная волна: станции в окне «Включить волну» зависят от времени суток.
-- Список станций лежит в stations.lua (подключается через require).
local stations = require("stations")

local function period(hour)
  if hour >= 5 and hour < 11 then return "morning" end
  if hour >= 11 and hour < 17 then return "day" end
  if hour >= 17 and hour < 22 then return "evening" end
  return "night"
end

local current

local function refresh()
  local p = period(tonumber(os.date("%H")))
  if p == current then return end
  current = p
  wave.clear_stations()
  for _, s in ipairs(stations[p]) do
    wave.add_station(s.title, s.station, s.subtitle)
  end
  log("станции волны обновлены: " .. p)
end

refresh()
timer.every(10 * 60 * 1000, refresh)
