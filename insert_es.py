import urllib.request
import urllib.error
import json
import sys

ES_URL = "http://localhost:9201"
SEARCH_JSON = r"d:\code\IdeaFiles\volunteerDuration\search.json"

with open(SEARCH_JSON, encoding="utf-8") as f:
    data = json.load(f)

hits = data["hits"]["hits"]
print(f"Loaded {len(hits)} documents")

# Build ndjson bulk body
lines = []
for h in hits:
    lines.append(json.dumps({"index": {"_index": h["_index"], "_id": h["_id"]}}, ensure_ascii=False))
    lines.append(json.dumps(h["_source"], ensure_ascii=False))

bulk_body = "\n".join(lines) + "\n"
payload = bulk_body.encode("utf-8")

req = urllib.request.Request(
    f"{ES_URL}/_bulk",
    data=payload,
    headers={"Content-Type": "application/x-ndjson; charset=utf-8"},
    method="POST"
)

try:
    resp = urllib.request.urlopen(req, timeout=10)
    result = json.loads(resp.read())
    errors = result.get("errors", True)
    took = result.get("took")
    items = result.get("items", [])
    print(f"took={took}ms, errors={errors}, items={len(items)}")
    if errors:
        for item in items:
            for op, v in item.items():
                if v.get("error"):
                    print(f"  ERROR: {v['_index']}/{v['_id']} -> {v['error']}")
    else:
        for item in items:
            for op, v in item.items():
                print(f"  {v['result']:7s} {v['_index']}/{v['_id']}")
except urllib.error.HTTPError as e:
    print(f"HTTP {e.code}: {e.read().decode()}")
except Exception as e:
    print(f"Error: {e}")
