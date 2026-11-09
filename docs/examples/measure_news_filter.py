"""Measures Filter Out Noise on news-filter-judge.json, and saves Gemini's real answers there.

Run by hand from the repository root, after changing NewsAssessor.PROMPT: python docs/examples/measure_news_filter.py
It reads the prompt from NewsAssessor.java and the key from .env. It sends every headline in three orders, in batches of
20 numbered as the app numbers them, so each headline gets one answer per order.
"""
import hashlib
import json
import pathlib
import random
import re
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
SET = ROOT / "docs" / "examples" / "news-filter-judge.json"
JAVA = ROOT / "backend" / "src" / "main" / "java" / "com" / "truesight" / "news" / "NewsAssessor.java"
FIELDS = ["index", "isEvent", "eventType", "severity", "confidence", "summary", "reason"]
SCHEMA = {"type": "OBJECT", "properties": {"assessments": {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
    "index": {"type": "INTEGER"}, "isEvent": {"type": "BOOLEAN"}, "eventType": {"type": "STRING"},
    "severity": {"type": "STRING"}, "confidence": {"type": "STRING"}, "summary": {"type": "STRING"},
    "reason": {"type": "STRING"}}, "required": FIELDS, "propertyOrdering": FIELDS}}}, "required": ["assessments"]}
BATCH = 20
ORDERS = 3


def prompt_text():
    block = re.search(r'PROMPT = """\n(.*?)""";', JAVA.read_text(encoding="utf-8"), re.S).group(1)
    return "\n".join(line[12:] for line in block.split("\n"))


def ask(prompt):
    env = dict(re.findall(r"^(GEMINI_API_KEY|GEMINI_MODEL)=(.+)$", (ROOT / ".env").read_text(encoding="utf-8"), re.M))
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{env['GEMINI_MODEL'].strip()}:generateContent"
    body = {"contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": 0, "responseMimeType": "application/json", "responseSchema": SCHEMA}}
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, json.dumps(body).encode(),
                                             {"Content-Type": "application/json", "x-goog-api-key": env["GEMINI_API_KEY"].strip()})
            with urllib.request.urlopen(request, timeout=180) as response:
                data = json.load(response)
            parts = data["candidates"][0]["content"]["parts"]
            return json.loads("".join(p.get("text", "") for p in parts if not p.get("thought")))["assessments"]
        except Exception:  # a slow or busy service: wait and try again
            if attempt == 3:
                raise
            time.sleep(5 * (attempt + 1))


def main():
    data = json.loads(SET.read_text(encoding="utf-8"))
    template = prompt_text()
    data["promptSha256"] = hashlib.sha256(template.encode()).hexdigest()
    cases = data["cases"]
    for case in cases:
        case["answer"] = []
    for order in range(ORDERS):
        shuffled = list(cases)
        random.Random(order).shuffle(shuffled)
        for start in range(0, len(shuffled), BATCH):
            batch = shuffled[start:start + BATCH]
            lines = "".join(f"{i + 1}. [{c['company']}] {c['headline']}\n" for i, c in enumerate(batch))
            answers = {a["index"]: a for a in ask(template + lines)}
            for i, case in enumerate(batch):
                case["answer"].append(answers.get(i + 1))
    SET.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    events = [c for c in cases if c["label"] == "E"]
    noise = [c for c in cases if c["label"] == "N"]
    for order in range(ORDERS):
        caught = sum(bool(c["answer"][order] and c["answer"][order]["isEvent"]) for c in events)
        alarms = sum(bool(c["answer"][order] and c["answer"][order]["isEvent"]) for c in noise)
        print(f"order {order + 1}: events caught {caught}/{len(events)}; false alarms {alarms}/{len(noise)}; "
              f"right {caught + len(noise) - alarms}/{len(cases)}")
    changing = sum(len({bool(a and a["isEvent"]) for a in c["answer"]}) > 1 for c in cases)
    print(f"verdicts changing across orders: {changing}")
    for c in cases:
        wrong = [i + 1 for i, a in enumerate(c["answer"]) if bool(a and a["isEvent"]) != (c["label"] == "E")]
        if wrong:
            print(f"  WRONG in orders {wrong} {c['label']} [{c['company']}] {c['headline']}")


main()
