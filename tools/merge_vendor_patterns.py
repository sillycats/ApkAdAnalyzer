#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把在线广告厂商特征合并进内置特征库（已存在的跳过）。

将 online_ad_vendors.json 中各家厂商的 features 聚合后，并入
patterns/ad_patterns_default.json 的对应分类；已在目标分类中
存在的条目跳过（保持 Python set 去重，顺序稳定）。内置缺的分类保持。
"""
import json
import sys
import os

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

BUILTIN = "patterns/ad_patterns_default.json"
VENDORS = "online_ad_vendors.json"

# online 字段名 -> 内置字段名（flutter 字段在在线库中名为 flutter_patterns，内置为 flutter_string_patterns）
KEY_MAP = {
    "sdk_packages": "sdk_packages",
    "class_keywords": "class_keywords",
    "method_patterns": "method_patterns",
    "url_patterns": "url_patterns",
    "ad_view_names": "ad_view_names",
    "ad_activities": "ad_activities",
    "ad_services": "ad_services",
    "ad_receivers": "ad_receivers",
    "ad_asset_paths": "ad_asset_paths",
    "lib_file_keywords": "lib_file_keywords",
    "asset_keywords": "asset_keywords",
    "string_patterns": "string_patterns",
    "flutter_patterns": "flutter_string_patterns",
}


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def main():
    # 可选：额外传入一个"补全"特征 JSON（如用户提供的 ad_patterns2.json），已存在跳过
    extra = None
    if len(sys.argv) >= 2:
        extra = load(sys.argv[1])
    builtin = load(BUILTIN)
    vendors = load(VENDORS)

    # 聚合各厂商 features
    agg = {}
    for v in vendors.get("vendors", []):
        for ok, lst in (v.get("features") or {}).items():
            agg.setdefault(ok, []).extend(lst)

    added_total = 0
    for ok, target_key in KEY_MAP.items():
        candidate = agg.get(ok)
        if not candidate:
            continue
        existing = set(builtin.get(target_key, []))
        new_items = []
        used = set(existing)
        for item in candidate:
            item = item.strip()
            if not item:
                continue
            if item in used:
                continue
            used.add(item)
            new_items.append(item)
        if new_items:
            builtin.setdefault(target_key, []).extend(new_items)
            added_total += len(new_items)
            print(f"  {target_key}: +{len(new_items)} (existing {len(existing)})")
        else:
            print(f"  {target_key}: 无新增（全部已存在）")

    # 合并额外补全文件（同名字段直接并集去重）
    if extra:
        extra_total = 0
        for k, lst in extra.items():
            if k not in builtin or not isinstance(lst, list):
                continue
            used = set(builtin[k])
            new_items = [x for x in lst if x.strip() and x not in used]
            if new_items:
                builtin[k].extend(new_items)
                extra_total += len(new_items)
                print(f"  [补全]{k}: +{len(new_items)}")
        added_total += extra_total

    # 写回（保持原有 key 顺序与格式）
    with open(BUILTIN, "w", encoding="utf-8") as f:
        json.dump(builtin, f, ensure_ascii=False, indent=2)
        f.write("\n")

    total = sum(len(v) for v in builtin.values())
    print(f"\n合并完成：新增 {added_total} 条，内置特征总数 {total}")


if __name__ == "__main__":
    main()