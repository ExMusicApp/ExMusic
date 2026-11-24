import ctypes, sys
lua = ctypes.CDLL("liblua5.4.so.0")
lua.luaL_newstate.restype = ctypes.c_void_p
for f in ("luaL_openlibs", "lua_settop", "lua_pushstring"):
    getattr(lua, f).restype = None
lua.luaL_loadfilex.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_char_p]
lua.lua_pcallk.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p]
lua.lua_tolstring.restype = ctypes.c_char_p
lua.lua_tolstring.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p]
lua.lua_pushstring.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
L = lua.luaL_newstate(); lua.luaL_openlibs(L)
st = lua.luaL_loadfilex(L, sys.argv[1].encode(), None)
if st != 0:
    print("SYNTAX/LOAD ERROR:", lua.lua_tolstring(L, -1, None).decode()); sys.exit(1)
lua.lua_pushstring(L, sys.argv[2].encode())
st = lua.lua_pcallk(L, 1, 0, 0, None, None)
if st != 0:
    print("RUNTIME ERROR:", lua.lua_tolstring(L, -1, None).decode()); sys.exit(1)
