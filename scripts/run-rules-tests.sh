#!/usr/bin/env bash
# Sunucu kuralları testlerini (supabase/tests/*.sql) sırayla çalıştırır. Her test kendi transaction'ında ROLLBACK ile biter.
# Kullanım: SUPABASE_DB_URL="postgresql://postgres:<şifre>@db.<ref>.supabase.co:5432/postgres" ./scripts/run-rules-tests.sh
set -euo pipefail

if [[ -z "${SUPABASE_DB_URL:-}" ]]; then
  echo "SUPABASE_DB_URL tanımlı değil (bağlantı adresi ortam değişkeninden okunur, depoya yazılmaz)." >&2
  exit 2
fi

cd "$(dirname "$0")/.."
failed=0
for test_file in supabase/tests/[0-9][0-9][0-9]_*.sql; do
  if psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -q -f "$test_file" >/dev/null; then
    echo "GEÇTİ  $test_file"
  else
    echo "KALDI  $test_file" >&2
    failed=1
  fi
done
exit $failed
