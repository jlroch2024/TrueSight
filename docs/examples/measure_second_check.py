"""Measures the second check's judge on second-check-judge.json, and saves Gemini's real answers there.

Run by hand from the repository root, after changing SecondCheck.PROMPT: python docs/examples/measure_second_check.py
It reads the prompt from SecondCheck.java and the key from .env. It sends each claim in the file to Gemini once.
"""
import concurrent.futures
import hashlib
import json
import pathlib
import re
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
SET = ROOT / "docs" / "examples" / "second-check-judge.json"
JAVA = ROOT / "backend" / "src" / "main" / "java" / "com" / "truesight" / "relationship" / "SecondCheck.java"
READINGS = ["SUPPLIES_THE_WRITER", "BUYS_FROM_THE_WRITER", "BOTH", "COMPETITOR", "ENDED", "PLANNED", "OTHER_TIE", "NOT_STATED"]
SCHEMA = {"type": "OBJECT", "properties": {"reading": {"type": "STRING", "enum": READINGS}, "reason": {"type": "STRING"}},
          "required": ["reading", "reason"]}


def template():
    block = re.search(r'PROMPT = """\n(.*?)""";', JAVA.read_text(encoding="utf-8"), re.S).group(1)
    lines = [line[12:] for line in block.split("\n")]
    return "\n".join(lines).replace("\\\n", "")


def fill(prompt, *values):
    for number, value in enumerate(values, 1):
        prompt = prompt.replace(f"%{number}$s", value)
    return prompt


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
            return json.loads("".join(p.get("text", "") for p in parts if not p.get("thought"))), data.get("usageMetadata", {})
        except Exception:  # a slow or busy service: wait and try again
            if attempt == 3:
                raise
            time.sleep(5 * (attempt + 1))


def agrees(case, reading):
    want = "SUPPLIES_THE_WRITER" if case["type"] == "SUPPLIER" else "BUYS_FROM_THE_WRITER"
    return reading in (want, "BOTH")


def main():
    data = json.loads(SET.read_text(encoding="utf-8"))
    prompt = template()
    data["promptSha256"] = hashlib.sha256(prompt.encode()).hexdigest()

    def judge(case):
        return ask(fill(prompt, case["writer"], case["form"], case["filingDate"], case["other"], case["passage"]))

    with concurrent.futures.ThreadPoolExecutor(4) as pool:
        results = list(pool.map(judge, data["cases"]))
    tokens_in = tokens_out = 0
    for case, (answer, usage) in zip(data["cases"], results):
        case["answer"] = answer
        tokens_in += usage.get("promptTokenCount", 0)
        tokens_out += usage.get("candidatesTokenCount", 0) + usage.get("thoughtsTokenCount", 0)
    SET.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    for name in sorted({c["set"] for c in data["cases"]}):
        cases = [c for c in data["cases"] if c["set"] == name]
        true_links = [c for c in cases if c["label"] is True]
        false_links = [c for c in cases if c["label"] is False]
        print(f"{name}: true links accepted {sum(agrees(c, c['answer']['reading']) for c in true_links)}/{len(true_links)}; "
              f"false links caught {sum(not agrees(c, c['answer']['reading']) for c in false_links)}/{len(false_links)}")
        for c in cases:
            right = c["label"] is None or agrees(c, c["answer"]["reading"]) == c["label"]
            if not right or c["label"] is None:
                print(f"  {'JUDGEMENT' if c['label'] is None else 'WRONG'} {c['answer']['reading']} | {c['writer']} {c['type']} {c['other']} | {c['answer']['reason']}")
    count = len(data["cases"])
    print(f"tokens per claim: {tokens_in // count} in, {tokens_out // count} out and thinking")


main()
