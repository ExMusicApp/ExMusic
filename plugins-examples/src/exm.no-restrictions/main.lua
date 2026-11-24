-- NoRestrictions: в приложении нет никакого кода для этого плагина. Всё делает Java-код отсюда (luajava), поэтому плагину нужна
-- ОТКЛЮЧЁННАЯ ПЕСОЧНИЦА: пользователь включает «Включить хуки» и «Отключить песочницу» в настройках плагина (по умолчанию выкл.).
-- Без этого luajava и app.viewmodel() не существуют, а хук не вызывается.
--
-- Идея: приложение решает «есть ли Плюс» по полю hasPlus у аккаунта в состоянии AppViewModel (и для обрезки до 30 секунд, и для значка).
-- Хук находит этот объект через рефлексию Java и ставит hasPlus = true; пока хук включён, делает это каждые 2 секунды
-- (приложение время от времени подменяет аккаунт на свежий с сервера). При выключении хука возвращает как было.

local patched = {}     -- аккаунты, у которых мы поменяли поле: [объект] = true
local timer_id = nil

-- Поле класса по имени, включая родительские классы (Java-рефлексия).
local function find_field(cls, name)
  local c = cls
  while c ~= nil do
    local ok, f = pcall(function() return c:getDeclaredField(name) end)
    if ok and f ~= nil then
      f:setAccessible(true)
      return f
    end
    c = c:getSuperclass()
  end
  return nil
end

local function current_account()
  local vm = app.viewmodel()
  if vm == nil then return nil end
  local f_state = find_field(vm:getClass(), "_state")
  if f_state == nil then log("NoRestrictions: в AppViewModel нет поля _state"); return nil end
  local ui = f_state:get(vm):getValue()
  if ui == nil then return nil end
  return ui:getAccount()
end

local function set_plus(acc, value)
  local f = find_field(acc:getClass(), "hasPlus")
  if f == nil then log("NoRestrictions: у Account нет поля hasPlus"); return false end
  f:setBoolean(acc, value)
  return true
end

local function patch()
  local acc = current_account()
  if acc == nil then return end
  if acc:getHasPlus() then return end      -- у аккаунта уже есть настоящий Плюс или мы его уже поправили
  if set_plus(acc, true) then patched[acc] = true end
end

local function revert()
  for acc in pairs(patched) do pcall(set_plus, acc, false) end
  patched = {}
end

-- Код хука: active == true — включить, false — выключить (в том числе при остановке плагина).
hooks.on("plus_local", function(active)
  if active then
    if timer_id == nil then timer_id = timer.every(2000, function() local ok, e = pcall(patch); if not ok then log("NoRestrictions: " .. tostring(e)) end end) end
    local ok, e = pcall(patch); if not ok then log("NoRestrictions: " .. tostring(e)) end
  else
    if timer_id ~= nil then timer.cancel(timer_id); timer_id = nil end
    pcall(revert)
  end
end)
