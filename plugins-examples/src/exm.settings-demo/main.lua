-- Своя страница настроек, созданная из Lua (ui.settings_page). Манифест настроек не описывает: всё рисует приложение
-- по тому, что вернёт render(). render вызывается при открытии страницы и после каждого действия пользователя.

local st = storage.get("state", { use_accent = false, accent = "#6750A4", mode = "soft", note = "", clicks = 0, volume = 100 })

local function save() storage.set("state", st) end

local function apply_theme()
  if st.use_accent then theme.set_accent(st.accent) else theme.set_accent(nil) end
end
apply_theme()

ui.settings_page {
  id = "demo",
  title = "Демо настроек",
  summary = "Страница создана из Lua",
  menu = true, -- пункт в главных Настройках (false — только в карточке плагина)

  render = function()
    local list = {
      { type = "section", title = "Оформление" },
      { type = "switch", id = "use_accent", title = "Свой цвет акцента", summary = "Перекрашивает приложение",
        value = st.use_accent,
        on_change = function(v) st.use_accent = v; save(); apply_theme() end },
    }
    -- Условные поля — просто обычный Lua: нужно поле — добавляем в список.
    if st.use_accent then
      list[#list + 1] = { type = "color", id = "accent", title = "Цвет акцента", value = st.accent,
        on_change = function(v) st.accent = v; save(); apply_theme() end }
    end
    list[#list + 1] = { type = "choice", id = "mode", title = "Режим", value = st.mode,
      options = { { value = "soft", title = "Мягкий" }, { value = "bright", title = "Яркий" } },
      on_change = function(v) st.mode = v; save() end }
    if st.mode == "bright" then
      list[#list + 1] = { type = "text", id = "note", title = "Подпись для яркого режима", max_length = 40, value = st.note,
        on_change = function(v) st.note = v; save() end }
    end

    list[#list + 1] = { type = "section", title = "Плеер" }
    list[#list + 1] = { type = "slider", id = "volume", title = "Громкость плагина", min = 0, max = 100, step = 5, value = st.volume,
      on_change = function(v) st.volume = v; save() end }
    list[#list + 1] = { type = "info", title = "Подсказка", text = "Эти компоненты нарисовало приложение, а значения и логику держит Lua." }

    list[#list + 1] = { type = "section", title = "Окна и модалки" }
    list[#list + 1] = { type = "button", title = "Подтверждение", summary = "ui.confirm",
      on_click = function()
        ui.confirm("Сбросить счётчик?", "Счётчик нажатий станет равен нулю.",
          function() st.clicks = 0; save(); ui.toast("Счётчик сброшен") end)
      end }
    list[#list + 1] = { type = "button", title = "Спросить имя", summary = "ui.prompt — окно с полем ввода",
      on_click = function()
        ui.prompt("Как вас зовут?", "Имя сохранится в плагине.", st.name or "", function(text)
          st.name = text; save(); ui.toast("Привет, " .. (text ~= "" and text or "незнакомец") .. "!")
        end)
      end }
    list[#list + 1] = { type = "button", title = "Своё окно", summary = "ui.dialog — три кнопки",
      on_click = function()
        ui.dialog {
          title = "Что делаем?", text = "Окно с тремя кнопками разных видов.",
          buttons = {
            { title = "Удалить", style = "danger", on_click = function() ui.toast("Удалено (на самом деле нет)") end },
            { title = "Позже", on_click = function() ui.toast("Позже") end },
            { title = "Готово", style = "primary", on_click = function() ui.toast("Готово!") end },
          },
          on_dismiss = function() ui.toast("Окно закрыто без выбора") end,
        }
      end }
    list[#list + 1] = { type = "button", title = "Открыть модалку", summary = "ui.sheet — своя нижняя шторка",
      on_click = function()
        local n = 0
        ui.sheet {
          id = "counter", title = "Счётчик в модалке", summary = "Одноразовая шторка",
          render = function()
            return {
              { type = "info", title = "Значение: " .. n },
              { type = "button", title = "Прибавить", on_click = function() n = n + 1 end },
              { type = "button", title = "Закрыть", on_click = function() ui.close_page("counter") end },
            }
          end,
          on_close = function() ui.toast("Итого в модалке: " .. n) end,
        }
      end }

    list[#list + 1] = { type = "section", title = "Действия" }
    list[#list + 1] = { type = "info", title = "Нажатий: " .. st.clicks }
    list[#list + 1] = { type = "button", title = "+1", on_click = function() st.clicks = st.clicks + 1; save() end }
    list[#list + 1] = { type = "button", title = "Сбросить всё", summary = "Вернёт значения по умолчанию",
      on_click = function()
        st = { use_accent = false, accent = "#6750A4", mode = "soft", note = "", clicks = 0, volume = 100 }
        save(); apply_theme()
        ui.alert("Готово", "Настройки демо сброшены.", function() ui.toast("Окно закрыто") end)
      end }
    return list
  end,
}
