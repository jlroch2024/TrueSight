"""Measures relationship finding on what ReportSection really sends, and saves Gemini's real answers.

Run by hand from the repository root, after changing ReportSection or the finder's prompt, with the backend compiled:
    python docs/examples/measure_report_reading.py <folder of extracted texts, named <ticker>.txt>
It reads the prompt from RelationshipFinder.java and the key from .env, and sends five annual reports to Gemini.
"""
import concurrent.futures
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
RESULT = ROOT / "docs" / "examples" / "report-reading-judge.json"
JAVA = ROOT / "backend" / "src" / "main" / "java" / "com" / "truesight" / "relationship" / "RelationshipFinder.java"
FIELDS = ["company", "alsoKnownAs", "commonName", "ticker", "relationship", "provides", "quote", "criticality", "criticalityQuote"]
SCHEMA = {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {k: {"type": "STRING"} for k in FIELDS},
                                     "required": FIELDS, "propertyOrdering": FIELDS}}

# Labelled by hand from each filing, before reading any answer. "in": named in the business and risk sections. "out":
# named only outside them. Each link is a regular expression for the names the report may use.
REPORTS = {
    "CRUS": {"name": "CIRRUS LOGIC, INC.", "form": "10-K",
             "in": {"Apple": "apple", "GlobalFoundries": "globalfoundries|global foundries", "TSMC": "taiwan semiconductor|tsmc",
                    "Advanced Semiconductor Engineering": "advanced semiconductor", "Amkor": "amkor", "STATS ChipPAC": "stats"},
             "out": {"Foxconn": "foxconn|hon hai", "Luxshare": "luxshare"}},
    "QCOM": {"name": "QUALCOMM INC/DE", "form": "10-K",
             "in": {"TSMC": "taiwan semiconductor|tsmc", "Samsung as supplier": "samsung.*:S", "Global Foundries": "globalfoundries|global foundries",
                    "Advanced Semiconductor Engineering": "advanced semiconductor", "Amkor": "amkor", "Siliconware Precision": "siliconware",
                    "STATS": "stats", "Samsung as customer": "samsung.*:C", "Apple": "apple", "Xiaomi": "xiaomi"},
             "out": {}},
    "GM": {"name": "GENERAL MOTORS Co", "form": "10-K", "in": {"Ultium Cells": "ultium"}, "out": {"LG Energy Solution": "lg energy|lges"}},
    "F": {"name": "FORD MOTOR CO", "form": "10-K", "in": {}, "out": {"Novelis": "novelis", "Ford Otosan": "otosan|otomotiv"}},
    "INTC": {"name": "INTEL CORP", "form": "10-K", "in": {"ASML": "asml"}, "out": {}},
}


def template():
    block = re.search(r'PROMPT = """\n(.*?)""";', JAVA.read_text(encoding="utf-8"), re.S).group(1)
    return "\n".join(line[12:] for line in block.split("\n")).replace("\\\n", "")


def ask(prompt):
    env = dict(re.findall(r"^(GEMINI_API_KEY|GEMINI_MODEL)=(.+)$", (ROOT / ".env").read_text(encoding="utf-8"), re.M))
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{env['GEMINI_MODEL'].strip()}:generateContent"
    body = {"contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": 0, "responseMimeType": "application/json", "responseSchema": SCHEMA}}
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, json.dumps(body).encode(),
                                             {"Content-Type": "application/json", "x-goog-api-key": env["GEMINI_API_KEY"].strip()})
            with urllib.request.urlopen(request, timeout=300) as response:
                data = json.load(response)
            parts = data["candidates"][0]["content"]["parts"]
            return json.loads("".join(p.get("text", "") for p in parts if not p.get("thought"))), data.get("usageMetadata", {})
        except Exception:  # a slow or busy service: wait and try again
            if attempt == 3:
                raise
            time.sleep(8 * (attempt + 1))


def main():
    folder = pathlib.Path(sys.argv[1])
    prompt = template()

    def read(item):
        ticker, label = item
        with tempfile.TemporaryDirectory() as temp:
            out = pathlib.Path(temp) / "sent.txt"
            subprocess.run(["java", "-cp", str(ROOT / "backend" / "target" / "classes"), str(ROOT / "docs" / "examples" / "SendSection.java"),
                            label["form"], str(folder / f"{ticker}.txt"), str(out)], check=True)
            sent = out.read_text(encoding="utf-8")
        answer, usage = ask(prompt.replace("%1$s", label["name"]).replace("%2$s", label["form"]).replace("%3$s", sent))
        return ticker, len(sent), answer, usage

    with concurrent.futures.ThreadPoolExecutor(3) as pool:
        results = list(pool.map(read, REPORTS.items()))

    saved, inside, outside = {}, [0, 0], [0, 0]
    tokens_in = tokens_out = 0
    for ticker, sent, answer, usage in results:
        names = "\n".join(f"{a['company']}:{a['relationship'][:1]}" for a in answer).lower().replace(":s", ":S").replace(":c", ":C")
        found = lambda rule: any(re.search(rule, line) for line in names.split("\n"))
        label = REPORTS[ticker]
        missed = [n for n, r in {**label["in"], **label["out"]}.items() if not found(r)]
        inside[0] += sum(found(r) for r in label["in"].values())
        inside[1] += len(label["in"])
        outside[0] += sum(found(r) for r in label["out"].values())
        outside[1] += len(label["out"])
        tokens_in += usage.get("promptTokenCount", 0)
        tokens_out += usage.get("candidatesTokenCount", 0) + usage.get("thoughtsTokenCount", 0)
        saved[ticker] = {"charactersSent": sent, "missed": missed, "answer": answer}
        print(f"{ticker}: sent {sent}, {len(answer)} links, missed {missed}: {', '.join(a['company'] for a in answer)}")
    print(f"labelled in the sections {inside[0]} of {inside[1]}; outside them {outside[0]} of {outside[1]}")
    print(f"tokens per report: {tokens_in // len(results)} in, {tokens_out // len(results)} out and thinking")
    RESULT.write_text(json.dumps({"inSections": inside, "outsideSections": outside, "reports": saved}, indent=1, ensure_ascii=False) + "\n",
                      encoding="utf-8", newline="\n")


main()
