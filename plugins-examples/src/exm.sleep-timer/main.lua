-- Таймер сна. Настройки (ползунки) — в карточке плагина.
-- Считает время, пока музыка реально играет. Через `minutes` минут плавно убавляет громкость за `fade_seconds`
-- секунд и ставит паузу. Если музыка стояла на паузе дольше 10 минут, отсчёт начинается заново.

local RESET_AFTER_IDLE = 10 * 60   -- пауза дольше этого сбрасывает таймер

local played = 0        -- сколько секунд играла музыка
local idle = 0          -- сколько секунд стоит пауза
local base_volume       -- громкость до затухания (nil, пока затухания нет)

local function restore_volume()
  if base_volume then
    player.set_volume(base_volume)
    base_volume = nil
  end
end

local function tick()
  local total = settings.get("minutes") * 60
  local fade = math.min(total, settings.get("fade_seconds"))

  if not player.is_playing() then
    idle = idle + 1
    if idle >= RESET_AFTER_IDLE then played = 0 end
    return
  end
  idle = 0
  if played == 0 then
    ui.toast("Таймер сна: пауза через " .. math.floor(total / 60) .. " мин")
  end
  played = played + 1

  local left = total - played
  if left <= 0 then
    player.pause()
    restore_volume()
    played = 0
    ui.toast("Таймер сна: спокойной ночи")
  elseif fade > 0 and left <= fade then
    if not base_volume then base_volume = player.volume() end
    player.set_volume(base_volume * left / fade)
  else
    restore_volume()   -- время увеличили, пока шло затухание
  end
end

timer.every(1000, tick)
events.on("unload", restore_volume)
