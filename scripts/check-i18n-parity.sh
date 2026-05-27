#!/usr/bin/env bash
# 校验 i18n properties 文件的 key 一致性。
# 以 messages_en.properties 为基准，输出缺失或多余的 key。
# 仅打印 warning，永不返回非零退出码，避免阻断 CI。

set -u

I18N_DIR="${1:-src/main/resources/i18n}"
BASELINE="${I18N_DIR}/messages_en.properties"

if [[ ! -f "${BASELINE}" ]]; then
  echo "::warning::baseline ${BASELINE} not found, skipping i18n parity check"
  exit 0
fi

# 提取 key（忽略注释与空行，取等号或冒号前的部分）
extract_keys() {
  local file="$1"
  grep -E '^[[:space:]]*[^#!=:[:space:]]' "${file}" \
    | sed -E 's/[[:space:]]*([^=:]+).*/\1/' \
    | sed -E 's/[[:space:]]+$//' \
    | sort -u
}

baseline_keys=$(extract_keys "${BASELINE}")
total_issues=0

for file in "${I18N_DIR}"/messages_*.properties; do
  [[ "${file}" == "${BASELINE}" ]] && continue
  locale=$(basename "${file}" .properties | sed 's/^messages_//')
  other_keys=$(extract_keys "${file}")

  missing=$(comm -23 <(echo "${baseline_keys}") <(echo "${other_keys}"))
  extra=$(comm -13 <(echo "${baseline_keys}") <(echo "${other_keys}"))

  if [[ -n "${missing}" || -n "${extra}" ]]; then
    echo "::warning file=${file}::locale '${locale}' is not in parity with en baseline"
    if [[ -n "${missing}" ]]; then
      echo "  Missing keys (defined in en but not in ${locale}):"
      echo "${missing}" | sed 's/^/    - /'
    fi
    if [[ -n "${extra}" ]]; then
      echo "  Extra keys (defined in ${locale} but not in en):"
      echo "${extra}" | sed 's/^/    + /'
    fi
    total_issues=$((total_issues + 1))
  fi
done

if [[ "${total_issues}" -eq 0 ]]; then
  echo "i18n parity check passed: all locales aligned with en baseline"
else
  echo "::warning::i18n parity check found ${total_issues} locale(s) with issues (non-blocking)"
fi

# 永远返回 0，由用户决策接受不完整 locale
exit 0
