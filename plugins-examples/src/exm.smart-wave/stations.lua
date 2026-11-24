-- Станции по времени суток. Идентификатор станции: тип:значение (см. PLUGINS.md).
-- Редактируйте под себя: title — название в окне волны, station — идентификатор, subtitle — подпись.
return {
  morning = {
    { title = "Просыпаемся",        station = "activity:wake-up",          subtitle = "Мягко войти в день" },
    { title = "Бодрое утро",        station = "mood:energetic",            subtitle = "Энергия на старт" },
  },
  day = {
    { title = "Фон для работы",     station = "activity:work-background",  subtitle = "Не отвлекает" },
    { title = "Хорошее настроение", station = "mood:happy",                subtitle = "Весёлое и светлое" },
  },
  evening = {
    { title = "Спокойный вечер",    station = "mood:calm",                 subtitle = "Выдохнуть" },
    { title = "Тренировка",         station = "activity:workout",          subtitle = "Если хочется движения" },
  },
  night = {
    { title = "Засыпаем",           station = "activity:fall-asleep",      subtitle = "Тихо и ровно" },
    { title = "Ночное спокойствие", station = "mood:calm",                 subtitle = "Без резких звуков" },
  },
}
