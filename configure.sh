#!/bin/sh
set -eu

# A mounted file is authoritative; a directory mount also supports atomic replacements.
if [ -e /config/settings.json ]; then
  jq -e 'type == "object" and (.maps | type == "array")' /config/settings.json > /dev/null
  ln -sf /config/settings.json /usr/share/nginx/html/settings.json
  exit 0
fi

# Remove a previous mount symlink before generating fallback settings.
rm -f /usr/share/nginx/html/settings.json
export MAP_DATA_ROOT="${MAP_DATA_ROOT:-maps}"
export MAP_IDS="${MAP_IDS:-}"
export LIVE_DATA_ROOT="${LIVE_DATA_ROOT:-$MAP_DATA_ROOT}"

# Generate JSON safely, without changing the official webapp.
jq -en '
  def root:
    if . == "maps" then .
    elif test("^https?://[^/\\s?#]+(/[^\\s?#]*)?$")
    then sub("/+$"; "")
    else error("Data roots must be absolute HTTP(S) URLs without queries or fragments") end;
  (env.MAP_IDS | if . == "" then [] else split(",") | map(gsub("^\\s+|\\s+$"; "")) end) as $maps |
  if ($maps | all(test("^[A-Za-z0-9_-]+$"))) then
    {
      version: "5.24",
      useCookies: true,
      mapDataRoot: (env.MAP_DATA_ROOT | root),
      liveDataRoot: (env.LIVE_DATA_ROOT | root),
      maps: $maps,
      clientDecompression: true
    }
  else error("MAP_IDS must contain comma-separated map IDs (letters, numbers, _ or -)") end
' > /usr/share/nginx/html/settings.json
