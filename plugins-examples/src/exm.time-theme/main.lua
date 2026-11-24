-- Тема по времени суток.
-- Раз в минуту смотрит на часы и выбирает цвет акцента.
-- Настройка amoled_night (переключатель в карточке плагина) решает, включать ли чёрный фон ночью.

-- Часы заданы от и до (до — не включительно). Ночь идёт через полночь: 22..29 = 22:00–05:00.
local PALETTE = {
  { from = 5,  to = 11, accent = "#FFB74D", amoled = false, name = "утро"  },
  { from = 11, to = 17, accent = "#4FC3F7", amoled = false, name = "день"  },
  { from = 17, to = 22, accent = "#BA68C8", amoled = false, name = "вечер" },
  { from = 22, to = 29, accent = "#5C6BC0", amoled = true,  name = "ночь"  },
}

local function slot_for(hour)
  if hour < 5 then hour = hour + 24 end
  for _, s in ipairs(PALETTE) do
    if hour >= s.from and hour < s.to then return s end
  end
  return PALETTE[#PALETTE]
end

local current

local function apply()
  local s = slot_for(tonumber(os.date("%H")))
  if s ~= current then
    current = s
    theme.set_accent(s.accent)
    theme.set_amoled(s.amoled and settings.get("amoled_night"))
    log("время суток: " .. s.name)
  end
end

apply()
timer.every(60 * 1000, apply)

-- Настройку поменяли — применяем сразу, не ждём минуту.
events.on("setting_changed", function()
  current = nil
  apply()
end)
