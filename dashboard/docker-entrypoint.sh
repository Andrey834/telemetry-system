#!/bin/sh
set -eu

envsubst '${QUERY_SERVICE_URL}' < /usr/share/nginx/html/env.template.js > /usr/share/nginx/html/env.js

exec nginx -g 'daemon off;'
